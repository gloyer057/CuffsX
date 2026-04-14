# Дизайн: cuffsx — Мод наручников для Minecraft 1.20.1 (Fabric)

## Обзор

Мод `cuffsx` добавляет механику наручников на Fabric 1.20.1. Два предмета — `handcuffs_hands` и `handcuffs_legs` — надеваются на других игроков через ПКМ. Игрок в наручниках на руках лишается всех взаимодействий с миром; игрок в наручниках на ногах не может двигаться. Состояние сохраняется через `PersistentState` и переживает рестарт сервера. Управление через команды `/cuffsx` с пермишонами через Fabric Permissions API.

Все ограничения применяются исключительно на серверной стороне. Клиент получает стандартные пакеты отмены действий от сервера — отдельная клиентская логика не требуется.

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                        Cuffsx.java                          │
│  (ModInitializer — регистрация предметов, событий, команд)  │
└────────┬────────────────────────────────────────────────────┘
         │ регистрирует
         ▼
┌────────────────────┐    ┌──────────────────────────────────┐
│   ModItems.java    │    │         CuffManager.java         │
│  ModItemGroups.java│    │  (singleton, бизнес-логика)      │
└────────────────────┘    └──────┬───────────────────────────┘
                                 │ читает/пишет
                                 ▼
                          ┌──────────────────┐
                          │  CuffState.java  │
                          │ (PersistentState)│
                          └──────────────────┘

Fabric API Events:
  UseEntityCallback      → CuffManager.applyCuffs / removeCuffs
  AttackBlockCallback    → блокировка (HANDS)
  UseBlockCallback       → блокировка (HANDS)
  UseItemCallback        → блокировка (HANDS)
  ServerTickEvents       → телепорт (LEGS)
  ServerEntityEvents     → повторное применение при join

Mixins:
  ServerPlayerEntityMixin     → jump(), interactBlock(), interactItem(), attack()
  PlayerScreenHandlerMixin    → onSlotClick()
```

### Поток надевания наручников

```
Игрок A (ПКМ по Игроку B с handcuffs_hands в руке)
  → UseEntityCallback.INTERACT_ENTITY
  → CuffManager.applyCuffs(applier, target, CuffType.HANDS)
    → Validator.isEnabled() ?
    → !isCuffed(target, HANDS) ?
    → CuffState.addRecord(CuffRecord)
    → applier.getMainHandStack().decrement(1)
    → CuffLog.log(APPLY, ...)
    → applyRestrictions(target, HANDS)
```

### Поток снятия наручников

```
Игрок A (ПКМ по Игроку B, у A нет наручников)
  → UseEntityCallback.INTERACT_ENTITY
  → CuffManager.removeCuffs(applier, target)
    → !isCuffed(applier, any) ?
    → isCuffed(target, any) ?
    → CuffState.removeRecord(target, приоритет HANDS > LEGS)
    → applier.getInventory().insertStack(HandcuffsItem)
    → CuffLog.log(REMOVE, ...)
    → removeRestrictions(target, removedType)
```

## Компоненты и интерфейсы

### item/HandcuffsItem.java

```java
public class HandcuffsItem extends Item {
    private final CuffType cuffType;

    public HandcuffsItem(CuffType cuffType) {
        super(new Item.Settings().maxCount(16));
        this.cuffType = cuffType;
    }

    public CuffType getCuffType() { return cuffType; }
}
```

### item/ModItems.java

Регистрирует оба предмета через `Registry.register(Registries.ITEM, ...)`:
- `cuffsx:handcuffs_hands` → `new HandcuffsItem(CuffType.HANDS)`
- `cuffsx:handcuffs_legs`  → `new HandcuffsItem(CuffType.LEGS)`

### item/ModItemGroups.java

Регистрирует вкладку `cuffsx` через `Registry.register(Registries.ITEM_GROUP, ...)` с обоими предметами.

### cuff/CuffType.java

```java
public enum CuffType {
    HANDS, LEGS;

    public String toNbt() { return name(); }
    public static CuffType fromNbt(String s) { return valueOf(s); }
}
```

### cuff/CuffRecord.java

```java
public record CuffRecord(
    UUID targetUUID,
    UUID applierUUID,
    CuffType cuffType,
    long timestamp,       // System.currentTimeMillis()
    Vec3d lockedPos,      // блочные координаты цели в момент надевания
    String applierName,
    String targetName
) {
    public NbtCompound toNbt() { ... }
    public static CuffRecord fromNbt(NbtCompound nbt) { ... }
}
```

### cuff/CuffState.java

```java
public class CuffState extends PersistentState {
    public static final String KEY = "cuffsx_state";

    private final Map<UUID, Set<CuffRecord>> records = new HashMap<>();

    public void addRecord(CuffRecord record) { ... }
    public boolean removeRecord(UUID targetUUID, CuffType type) { ... }
    public Set<CuffRecord> getRecords(UUID targetUUID) { ... }
    public Collection<CuffRecord> getAllRecords() { ... }
    public boolean hasCuff(UUID targetUUID, CuffType type) { ... }

    @Override public NbtCompound writeNbt(NbtCompound nbt) { ... }
    public static CuffState fromNbt(NbtCompound nbt) { ... }

    public static CuffState getOrCreate(MinecraftServer server) {
        return server.getOverworld()
            .getPersistentStateManager()
            .getOrCreate(CuffState::fromNbt, CuffState::new, KEY);
    }
}
```

Сериализация: каждый `CuffRecord` сериализуется в `NbtCompound`, список хранится в `NbtList` под ключом `"records"`.

### cuff/CuffManager.java

```java
public class CuffManager {
    private static boolean enabled = true;

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    /** Надеть наручники. Возвращает false если не удалось. */
    public static boolean applyCuffs(ServerPlayerEntity applier,
                                     ServerPlayerEntity target,
                                     CuffType type) { ... }

    /** Снять наручники. Возвращает снятый CuffType или null. */
    public static CuffType removeCuffs(ServerPlayerEntity applier,
                                       ServerPlayerEntity target) { ... }

    public static boolean isCuffed(ServerPlayerEntity player, CuffType type) { ... }
    public static boolean isCuffed(ServerPlayerEntity player) { ... }

    /** Применить серверные ограничения (вызывается при надевании и при join). */
    public static void applyRestrictions(ServerPlayerEntity player, CuffType type) { ... }

    /** Снять серверные ограничения. */
    public static void removeRestrictions(ServerPlayerEntity player, CuffType type) { ... }
}
```

`applyRestrictions` для `LEGS` устанавливает `EntityAttributeInstance` атрибута `EntityAttributes.GENERIC_MOVEMENT_SPEED` в 0 через `addPersistentModifier`. Для `HANDS` — флаг хранится в `CuffState`, проверяется в Mixin/событиях.

### cuff/CuffLog.java

```java
public class CuffLog {
    public enum Action { APPLY, REMOVE }

    public record LogEntry(
        long timestamp,
        Action action,
        String targetName,
        String applierName,
        CuffType cuffType,
        Vec3d coords
    ) {}

    private static final List<LogEntry> entries = new ArrayList<>();
    private static final long TTL_MS = 2 * 60 * 60 * 1000L; // 2 часа

    public static void log(Action action, CuffRecord record) { ... }

    /** Возвращает записи за последние 2 часа, удаляя устаревшие. */
    public static List<LogEntry> getRecent() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        entries.removeIf(e -> e.timestamp() < cutoff);
        return List.copyOf(entries);
    }
}
```

### command/CuffsxCommand.java

Регистрируется через `CommandRegistrationCallback`. Дерево команд:

```
/cuffsx
  reload   — требует cuffsx.reload
  list     — требует cuffsx.list
  enable   — требует cuffsx.enable
  disable  — требует cuffsx.disable
  log      — требует cuffsx.log
```

Пермишоны проверяются через `Permissions.require("cuffsx.<node>", 4)` из `me.lucko.fabric:fabric-permissions-api`.

### mixin/ServerPlayerEntityMixin.java

Цель: `net.minecraft.server.network.ServerPlayerEntity`

Инжекции:
- `@Inject` в `jump()` — если `isCuffed(LEGS)`, отменить прыжок
- `@Inject` в `interactBlock(...)` — если `isCuffed(HANDS)`, вернуть `ActionResult.FAIL`
- `@Inject` в `interactItem(...)` — если `isCuffed(HANDS)`, вернуть `ActionResult.FAIL`
- `@Inject` в `attack(Entity target)` — если `isCuffed(HANDS)`, отменить атаку

Дополнительно через Fabric API события в `Cuffsx.java`:
- `AttackBlockCallback.EVENT` — если `isCuffed(HANDS)`, вернуть `ActionResult.FAIL`
- `UseBlockCallback.EVENT` — если `isCuffed(HANDS)`, вернуть `ActionResult.FAIL`
- `UseItemCallback.EVENT` — если `isCuffed(HANDS)`, вернуть `TypedActionResult.fail(...)`

### mixin/PlayerScreenHandlerMixin.java

Цель: `net.minecraft.screen.PlayerScreenHandler`

Инжекция:
- `@Inject` в `onSlotClick(int slot, int button, SlotActionType actionType, PlayerEntity player)` — если `isCuffed(HANDS)`, отменить действие через `ci.cancel()`

### Регистрация событий в Cuffsx.java

```java
// Надевание/снятие
UseEntityCallback.INTERACT_ENTITY.register((player, world, hand, entity, hitResult) -> {
    if (world.isClient || !(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
    ServerPlayerEntity applier = (ServerPlayerEntity) player;
    ItemStack stack = applier.getStackInHand(hand);
    if (stack.getItem() instanceof HandcuffsItem hi) {
        CuffManager.applyCuffs(applier, target, hi.getCuffType());
        return ActionResult.SUCCESS;
    }
    if (!CuffManager.isCuffed(applier) && CuffManager.isCuffed(target)) {
        CuffManager.removeCuffs(applier, target);
        return ActionResult.SUCCESS;
    }
    return ActionResult.PASS;
});

// Телепорт при LEGS
ServerTickEvents.END_SERVER_TICK.register(server -> {
    CuffState state = CuffState.getOrCreate(server);
    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
        state.getRecords(player.getUuid()).stream()
            .filter(r -> r.cuffType() == CuffType.LEGS)
            .findFirst()
            .ifPresent(r -> {
                if (player.getPos().distanceTo(r.lockedPos()) > 0.1) {
                    player.teleport(r.lockedPos().x, r.lockedPos().y, r.lockedPos().z);
                }
            });
    }
});

// Повторное применение ограничений при join
ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
    if (entity instanceof ServerPlayerEntity player) {
        CuffState state = CuffState.getOrCreate(world.getServer());
        state.getRecords(player.getUuid()).forEach(r ->
            CuffManager.applyRestrictions(player, r.cuffType()));
    }
});
```

## Модели данных

### CuffRecord (NBT-схема)

```
NbtCompound {
  "targetUUID":  String  (UUID.toString())
  "applierUUID": String
  "cuffType":    String  ("HANDS" | "LEGS")
  "timestamp":   Long
  "lockedX":     Double
  "lockedY":     Double
  "lockedZ":     Double
  "applierName": String
  "targetName":  String
}
```

### CuffState (NBT-схема)

```
NbtCompound {
  "records": NbtList [
    NbtCompound { ...CuffRecord... },
    ...
  ]
}
```

### LogEntry (in-memory)

```
LogEntry {
  timestamp:   long
  action:      Action (APPLY | REMOVE)
  targetName:  String
  applierName: String
  cuffType:    CuffType
  coords:      Vec3d
}
```

### Ресурсные файлы

```
assets/cuffsx/
  models/item/
    handcuffs_hands.json   → { "parent": "item/generated", "textures": { "layer0": "cuffsx:item/handcuffs_hands" } }
    handcuffs_legs.json    → { "parent": "item/generated", "textures": { "layer0": "cuffsx:item/handcuffs_legs" } }
  textures/item/
    handcuffs_hands.png    (заглушка 16x16)
    handcuffs_legs.png     (заглушка 16x16)
  lang/
    en_us.json             → { "item.cuffsx.handcuffs_hands": "Handcuffs (Hands)", "item.cuffsx.handcuffs_legs": "Handcuffs (Legs)", "itemGroup.cuffsx.cuffsx": "Cuffsx" }
    ru_ru.json             → { "item.cuffsx.handcuffs_hands": "Наручники (руки)", "item.cuffsx.handcuffs_legs": "Наручники (ноги)", "itemGroup.cuffsx.cuffsx": "Cuffsx" }
```

### build.gradle — дополнения

```groovy
repositories {
    maven { url "https://oss.sonatype.org/content/repositories/snapshots" }
}

dependencies {
    // ... существующие зависимости ...
    modImplementation "me.lucko.fabric:fabric-permissions-api:0.2-SNAPSHOT"
}
```

## Свойства корректности

*Свойство — это характеристика или поведение, которое должно выполняться при всех допустимых выполнениях системы. Свойства служат мостом между читаемыми человеком спецификациями и машинно-верифицируемыми гарантиями корректности.*

### Property 1: Надевание создаёт запись в CuffState

*Для любых* двух игроков A и B, если A нажимает ПКМ по B с `HandcuffsItem` типа T в руке и глобальный флаг включён и B ещё не в наручниках типа T, то после взаимодействия `CuffState.hasCuff(B.uuid, T)` должен вернуть `true`, а запись должна содержать UUID обоих игроков, тип T, метку времени и координаты B.

**Validates: Requirements 2.1, 2.2, 2.4, 2.5**

### Property 2: Надевание уменьшает стек на 1

*Для любого* игрока A с `HandcuffsItem` в руке (размер стека N > 0), после успешного надевания наручников размер стека должен стать N - 1.

**Validates: Requirements 2.3**

### Property 3: Снятие удаляет запись из CuffState и возвращает предмет

*Для любых* двух игроков A (без наручников) и B (в наручниках), после того как A нажимает ПКМ по B, запись для B должна быть удалена из `CuffState`, а в инвентаре A должен появиться соответствующий `HandcuffsItem`.

**Validates: Requirements 3.1, 3.2, 3.3**

### Property 4: Игрок в наручниках не может снимать наручники с других

*Для любого* игрока A, у которого надет любой тип наручников, попытка снять наручники с любого другого игрока B должна быть отклонена, и `CuffState` не должен изменяться.

**Validates: Requirements 3.4**

### Property 5: Повторное надевание того же типа — идемпотентно

*Для любого* игрока B, у которого уже надет тип T, повторная попытка надеть тип T должна быть отклонена: `CuffState` не изменяется, предмет не расходуется.

**Validates: Requirements 2.6**

### Property 6: Disabled флаг блокирует надевание, но не снятие

*Для любых* двух игроков A и B, пока глобальный флаг `CuffManager.isEnabled()` равен `false`, попытка надеть наручники должна быть отклонена и `CuffState` не должен изменяться. При этом снятие наручников должно по-прежнему выполняться успешно.

**Validates: Requirements 2.7, 9.3, 9.4**

### Property 7: HANDS блокирует все взаимодействия с миром

*Для любого* игрока P, у которого надеты наручники `HANDS`, все следующие действия должны быть отменены: атака блока, взаимодействие с блоком, использование предмета, атака сущности, перемещение предметов в инвентаре.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6**

### Property 8: LEGS устанавливает скорость в 0

*Для любого* игрока P, у которого надеты наручники `LEGS`, значение атрибута `GENERIC_MOVEMENT_SPEED` должно быть равно 0.

**Validates: Requirements 5.1**

### Property 9: LEGS блокирует прыжок

*Для любого* игрока P, у которого надеты наручники `LEGS`, вызов `jump()` не должен изменять вертикальную скорость игрока.

**Validates: Requirements 5.2**

### Property 10: LEGS телепортирует при отклонении позиции

*Для любого* игрока P, у которого надеты наручники `LEGS` с зафиксированной позицией `lockedPos`, если в конце тика позиция P отличается от `lockedPos` более чем на 0.1 блока, то P должен быть телепортирован обратно на `lockedPos`.

**Validates: Requirements 5.3**

### Property 11: Round-trip сериализации CuffRecord

*Для любого* объекта `CuffRecord` с произвольными допустимыми значениями полей, `CuffRecord.fromNbt(record.toNbt())` должен вернуть объект, эквивалентный исходному по всем полям.

**Validates: Requirements 6.2, 6.3, 6.5**

### Property 12: Ограничения повторно применяются при входе игрока

*Для любого* игрока P, для которого в `CuffState` есть активные `CuffRecord`, после события `ENTITY_LOAD` все соответствующие ограничения должны быть применены (скорость = 0 для LEGS, флаги блокировки для HANDS).

**Validates: Requirements 6.4**

### Property 13: Команды без пермишона отклоняются

*Для любого* игрока P, у которого нет узла пермишона `cuffsx.<subcommand>`, выполнение соответствующей подкоманды `/cuffsx` должно быть отклонено с сообщением об отсутствии прав, и никакого побочного эффекта не должно происходить.

**Validates: Requirements 7.2, 8.3, 9.5, 10.3, 11.3**

### Property 14: Журнал хранит записи не менее 2 часов

*Для любой* записи `LogEntry`, добавленной в `CuffLog`, она должна присутствовать в результате `CuffLog.getRecent()` до тех пор, пока с момента её создания не прошло 2 часа.

**Validates: Requirements 10.4**

## Обработка ошибок

| Ситуация | Поведение |
|---|---|
| Applier нажимает ПКМ по себе | `applier == target` → `ActionResult.PASS`, ничего не происходит |
| Цель не является `ServerPlayerEntity` | `UseEntityCallback` возвращает `PASS` |
| Инвентарь снявшего полон | `insertStack` возвращает остаток; предмет дропается у ног игрока |
| `CuffState` не инициализирован при join | `getOrCreate` гарантирует создание нового пустого состояния |
| Атрибут `MOVEMENT_SPEED` уже имеет модификатор | Используется фиксированный UUID модификатора; повторный `addPersistentModifier` заменяет существующий |
| Игрок выходит с сервера при надетых LEGS | `lockedPos` сохранена в `CuffState`; при следующем входе ограничения восстанавливаются |
| Команда выполняется из консоли сервера | `Permissions.require(...)` с fallback `opLevel >= 4`; консоль имеет уровень 4 |

## Стратегия тестирования

### Юнит-тесты (конкретные примеры)

- Регистрация предметов: `Registries.ITEM.get(new Identifier("cuffsx", "handcuffs_hands"))` не null, `maxCount == 16`
- Ключ `CuffState`: `CuffState.KEY.equals("cuffsx_state")`
- Команда `/cuffsx list` при пустом состоянии: вывод содержит "Нет игроков в наручниках."
- Команда `/cuffsx enable` / `/cuffsx disable`: флаг меняется корректно
- Команда `/cuffsx log` при пустом журнале: вывод содержит "Нет взаимодействий за последние 2 часа."

### Property-based тесты

Используется библиотека **[jqwik](https://jqwik.net/)** (JUnit 5 property-based testing для Java).

Конфигурация: минимум 100 итераций на каждый тест (`@Property(tries = 100)`).

Каждый тест помечается комментарием в формате:
`// Feature: cuffsx-handcuffs-mod, Property N: <текст свойства>`

**P11 — Round-trip сериализации CuffRecord** (наиболее критичный):
```java
// Feature: cuffsx-handcuffs-mod, Property 11: CuffRecord round-trip serialization
@Property(tries = 100)
void cuffRecordRoundTrip(@ForAll("cuffRecords") CuffRecord record) {
    assertEquals(record, CuffRecord.fromNbt(record.toNbt()));
}
```

**P5 — Идемпотентность надевания**:
```java
// Feature: cuffsx-handcuffs-mod, Property 5: duplicate cuff application is idempotent
@Property(tries = 100)
void duplicateCuffIsRejected(@ForAll UUID targetUUID, @ForAll CuffType type) {
    CuffState state = new CuffState();
    CuffRecord r = makeRecord(targetUUID, type);
    state.addRecord(r);
    int before = state.getAllRecords().size();
    state.addRecord(r); // повторное добавление
    assertEquals(before, state.getAllRecords().size());
}
```

**P14 — Журнал хранит записи 2 часа**:
```java
// Feature: cuffsx-handcuffs-mod, Property 14: log entries retained for 2 hours
@Property(tries = 100)
void logRetainsEntriesWithinTtl(@ForAll("recentEntries") LogEntry entry) {
    // entry.timestamp() в пределах последних 2 часов
    assertTrue(CuffLog.getRecent().contains(entry));
}
```

Остальные свойства (P1–P10, P12–P13) реализуются аналогично с генераторами случайных UUID, имён игроков, координат и типов наручников.

### Интеграционные тесты

Для тестирования Mixin и событий Fabric API используется **[Fabric Test Framework](https://github.com/FabricMC/fabric/tree/1.20.1/fabric-gametest-api-v1)** (GameTest API). Тесты запускаются в игровом окружении через `./gradlew runGametest`.
