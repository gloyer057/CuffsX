# План реализации: cuffsx — Мод наручников для Minecraft 1.20.1 (Fabric)

## Обзор

Реализация мода поэтапно: сначала инфраструктура сборки и ресурсы, затем модели данных, бизнес-логика, миксины, события и команды. Каждый шаг компилируется и интегрируется с предыдущим.

## Задачи

- [x] 1. Настройка сборки и конфигурационных файлов
  - [x] 1.1 Обновить `build.gradle`: добавить репозиторий sonatype snapshots и зависимость `fabric-permissions-api:0.2-SNAPSHOT`
    - В блок `repositories`: `maven { url "https://oss.sonatype.org/content/repositories/snapshots" }`
    - В блок `dependencies`: `modImplementation "me.lucko.fabric:fabric-permissions-api:0.2-SNAPSHOT"`
    - _Requirements: 11.1, 12.1_
  - [x] 1.2 Обновить `src/main/resources/fabric.mod.json`: удалить entrypoint `fabric-datagen`, добавить `"fabric-permissions-api": "*"` в `depends`
    - _Requirements: 11.1, 12.2_
  - [x] 1.3 Обновить `src/main/resources/cuffsx.mixins.json`: добавить `"ServerPlayerEntityMixin"` и `"PlayerScreenHandlerMixin"` в массив `"mixins"`
    - _Requirements: 4.1-4.6, 5.2_

- [x] 2. Регистрация предметов и креативной вкладки
  - [x] 2.1 Создать `src/main/java/org/gloyer057/cuffsx/cuff/CuffType.java` — enum `HANDS`, `LEGS` с методами `toNbt()` и `static fromNbt(String)`
    - _Requirements: 1.1, 1.2_
  - [x] 2.2 Создать `src/main/java/org/gloyer057/cuffsx/item/HandcuffsItem.java` — extends `Item`, принимает `CuffType`, `maxCount(16)`, геттер `getCuffType()`
    - _Requirements: 1.1, 1.2_
  - [x] 2.3 Создать `src/main/java/org/gloyer057/cuffsx/item/ModItems.java` — регистрирует `cuffsx:handcuffs_hands` и `cuffsx:handcuffs_legs` через `Registry.register(Registries.ITEM, ...)`; статический метод `register()` для вызова из `Cuffsx.java`
    - _Requirements: 1.1, 1.2_
  - [x] 2.4 Создать `src/main/java/org/gloyer057/cuffsx/item/ModItemGroups.java` — регистрирует вкладку `cuffsx` через `Registry.register(Registries.ITEM_GROUP, ...)` с обоими предметами; статический метод `register()`
    - _Requirements: 1.1_

- [x] 3. Ресурсные файлы
  - [x] 3.1 Создать `src/main/resources/assets/cuffsx/models/item/handcuffs_hands.json` и `handcuffs_legs.json`
    - Содержимое: `{ "parent": "item/generated", "textures": { "layer0": "cuffsx:item/handcuffs_hands" } }` (аналогично для legs)
    - _Requirements: 1.3_
  - [x] 3.2 Создать файлы-заглушки текстур `src/main/resources/assets/cuffsx/textures/item/handcuffs_hands.png` и `handcuffs_legs.png` (минимальный валидный PNG 16x16)
    - _Requirements: 1.4_
  - [x] 3.3 Создать `src/main/resources/assets/cuffsx/lang/en_us.json` и `ru_ru.json`
    - en_us: `"item.cuffsx.handcuffs_hands": "Handcuffs (Hands)"`, `"item.cuffsx.handcuffs_legs": "Handcuffs (Legs)"`, `"itemGroup.cuffsx.cuffsx": "Cuffsx"`
    - ru_ru: `"item.cuffsx.handcuffs_hands": "Наручники (руки)"`, `"item.cuffsx.handcuffs_legs": "Наручники (ноги)"`, `"itemGroup.cuffsx.cuffsx": "Cuffsx"`
    - _Requirements: 1.5_

- [x] 4. Checkpoint — убедиться, что проект компилируется после задач 1-3
  - Убедиться, что все задачи 1-3 выполнены, проект собирается без ошибок. Задать вопрос пользователю при необходимости.

- [x] 5. Модели данных: CuffRecord и CuffState
  - [x] 5.1 Создать `src/main/java/org/gloyer057/cuffsx/cuff/CuffRecord.java` — Java record с полями `targetUUID`, `applierUUID`, `cuffType`, `timestamp`, `lockedPos` (Vec3d), `applierName`, `targetName`; методы `toNbt()` и `static fromNbt(NbtCompound)`
    - NBT-схема: UUID как String, `cuffType` как String, `timestamp` как Long, `lockedX/Y/Z` как Double, имена как String
    - _Requirements: 2.4, 6.2, 6.3, 6.5_
  - [x] 5.2 Написать property-тест для round-trip сериализации CuffRecord (jqwik)
    - **Property 11: Round-trip сериализации CuffRecord**
    - **Validates: Requirements 6.2, 6.3, 6.5**
    - Создать `src/test/java/org/gloyer057/cuffsx/CuffRecordRoundTripTest.java`
    - `@Property(tries = 100)` — `CuffRecord.fromNbt(record.toNbt())` должен быть равен исходному по всем полям
  - [x] 5.3 Создать `src/main/java/org/gloyer057/cuffsx/cuff/CuffState.java` — extends `PersistentState`, ключ `"cuffsx_state"`, хранит `Map<UUID, Set<CuffRecord>>`
    - Методы: `addRecord`, `removeRecord(UUID, CuffType)`, `getRecords(UUID)`, `getAllRecords()`, `hasCuff(UUID, CuffType)`, `writeNbt`, `static fromNbt`, `static getOrCreate(MinecraftServer)`
    - Сериализация: все записи в `NbtList` под ключом `"records"`
    - _Requirements: 6.1, 6.2, 6.3_
  - [x] 5.4 Написать property-тест для CuffState: добавление записи — `hasCuff` возвращает true; повторное добавление той же записи не увеличивает размер коллекции
    - **Property 5: Идемпотентность надевания**
    - **Validates: Requirements 2.6**

- [x] 6. CuffLog — журнал взаимодействий
  - [x] 6.1 Создать `src/main/java/org/gloyer057/cuffsx/cuff/CuffLog.java` — in-memory список `LogEntry` с TTL 2 часа (2 * 60 * 60 * 1000 мс)
    - Вложенный enum `Action { APPLY, REMOVE }` и record `LogEntry(timestamp, action, targetName, applierName, cuffType, coords)`
    - Метод `log(Action, CuffRecord)` добавляет запись; `getRecent()` удаляет устаревшие и возвращает `List.copyOf`
    - _Requirements: 10.1, 10.4_
  - [x] 6.2 Написать property-тест для CuffLog: запись, добавленная в пределах 2 часов, присутствует в `getRecent()`
    - **Property 14: Журнал хранит записи не менее 2 часов**
    - **Validates: Requirements 10.4**

- [x] 7. CuffManager — бизнес-логика
  - [x] 7.1 Создать `src/main/java/org/gloyer057/cuffsx/cuff/CuffManager.java` — статический класс с флагом `enabled = true`
    - `applyCuffs(applier, target, type)`: проверяет `isEnabled()` и дубликат; создаёт `CuffRecord`; `CuffState.addRecord`; `stack.decrement(1)`; `CuffLog.log(APPLY, ...)`; `applyRestrictions(target, type)`; возвращает `boolean`
    - `removeCuffs(applier, target)`: проверяет, что applier не в наручниках и target в наручниках; `CuffState.removeRecord` (приоритет HANDS > LEGS); `insertStack` в инвентарь applier (при полном инвентаре — дроп у ног); `CuffLog.log(REMOVE, ...)`; `removeRestrictions(target, type)`; возвращает снятый `CuffType` или `null`
    - `applyRestrictions(player, LEGS)`: устанавливает `GENERIC_MOVEMENT_SPEED` в 0 через `addPersistentModifier` с фиксированным UUID модификатора
    - `removeRestrictions(player, LEGS)`: удаляет модификатор скорости по UUID
    - _Requirements: 2.1-2.8, 3.1-3.5, 5.1, 9.3, 9.4_

- [x] 8. Checkpoint — убедиться, что модели данных и бизнес-логика компилируются
  - Убедиться, что задачи 5-7 выполнены без ошибок компиляции. Задать вопрос пользователю при необходимости.

- [x] 9. Миксины
  - [x] 9.1 Создать `src/main/java/org/gloyer057/cuffsx/mixin/ServerPlayerEntityMixin.java` — `@Mixin(ServerPlayerEntity.class)`
    - `@Inject(method = "jump", at = @At("HEAD"), cancellable = true)`: если `CuffManager.isCuffed(this, CuffType.LEGS)` — `ci.cancel()`
    - `@Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)`: если `CuffManager.isCuffed(this, CuffType.HANDS)` — `ci.cancel()`
    - `@Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)`: если `CuffManager.isCuffed(this, CuffType.HANDS)` — `ci.cancel()`
    - `@Inject(method = "attack", at = @At("HEAD"), cancellable = true)`: если `CuffManager.isCuffed(this, CuffType.HANDS)` — `ci.cancel()`
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 5.2_
  - [x] 9.2 Создать `src/main/java/org/gloyer057/cuffsx/mixin/PlayerScreenHandlerMixin.java` — `@Mixin(PlayerScreenHandler.class)`
    - `@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)`: если `player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS)` — `ci.cancel()`
    - _Requirements: 4.6_

- [x] 10. Регистрация событий и финальная сборка Cuffsx.java
  - [x] 10.1 В `Cuffsx.onInitialize()` вызвать `ModItems.register()` и `ModItemGroups.register()`
    - _Requirements: 1.1, 1.2_
  - [x] 10.2 Зарегистрировать `UseEntityCallback.INTERACT_ENTITY`: если в руке `HandcuffsItem` — `CuffManager.applyCuffs`; иначе если applier без наручников и target в наручниках — `CuffManager.removeCuffs`; `applier == target` — `PASS`
    - _Requirements: 2.1, 2.2, 3.1_
  - [x] 10.3 Зарегистрировать `AttackBlockCallback.EVENT` и `UseBlockCallback.EVENT`: если `CuffManager.isCuffed(player, HANDS)` — `ActionResult.FAIL`
    - _Requirements: 4.1, 4.3_
  - [x] 10.4 Зарегистрировать `UseItemCallback.EVENT`: если `CuffManager.isCuffed(player, HANDS)` — `TypedActionResult.fail(stack)`
    - _Requirements: 4.4_
  - [x] 10.5 Зарегистрировать `ServerTickEvents.END_SERVER_TICK`: для каждого игрока с LEGS-записью в `CuffState` — если `player.getPos().distanceTo(lockedPos) > 0.1` — `player.teleport(lockedPos.x, lockedPos.y, lockedPos.z)`
    - _Requirements: 5.3_
  - [x] 10.6 Зарегистрировать `ServerEntityEvents.ENTITY_LOAD`: если entity — `ServerPlayerEntity`, получить `CuffState.getOrCreate(server)` и вызвать `CuffManager.applyRestrictions` для каждой активной записи
    - _Requirements: 6.4_
  - [x] 10.7 Зарегистрировать `CommandRegistrationCallback` для `CuffsxCommand.register(dispatcher)`
    - _Requirements: 7.1, 8.1, 9.1, 10.1_

- [x] 11. Команды `/cuffsx`
  - [x] 11.1 Создать `src/main/java/org/gloyer057/cuffsx/command/CuffsxCommand.java` — статический метод `register(CommandDispatcher<ServerCommandSource>)`
    - `/cuffsx reload` — `Permissions.require("cuffsx.reload", 4)`; сбрасывает `CuffManager.setEnabled(true)`, отправляет подтверждение
    - `/cuffsx list` — `Permissions.require("cuffsx.list", 4)`; выводит все активные `CuffRecord` (имя цели, тип, имя надевшего, время с момента надевания) или "Нет игроков в наручниках."
    - `/cuffsx enable` — `Permissions.require("cuffsx.enable", 4)`; `CuffManager.setEnabled(true)`, подтверждение
    - `/cuffsx disable` — `Permissions.require("cuffsx.disable", 4)`; `CuffManager.setEnabled(false)`, подтверждение
    - `/cuffsx log` — `Permissions.require("cuffsx.log", 4)`; выводит `CuffLog.getRecent()` (имя цели, надевший, тип, действие, координаты, время) или "Нет взаимодействий за последние 2 часа."
    - При отсутствии пермишона: сообщение "У вас нет прав для выполнения этой команды."
    - _Requirements: 7.1, 7.2, 8.1, 8.2, 8.3, 9.1, 9.2, 9.5, 10.1, 10.2, 10.3, 11.1, 11.2, 11.3_

- [x] 12. Финальный checkpoint — полная сборка
  - Убедиться, что все задачи выполнены и `./gradlew build` проходит без ошибок. Задать вопрос пользователю при необходимости.

## Примечания

- Задачи, помеченные `*`, являются опциональными property-тестами (jqwik) и могут быть пропущены для быстрого MVP
- Каждая задача ссылается на конкретные требования для трассируемости
- Checkpoint-задачи обеспечивают инкрементальную валидацию
- Все ограничения применяются на серверной стороне; отдельная клиентская логика не требуется
- Для property-тестов нужно добавить jqwik в `build.gradle`: `testImplementation "net.jqwik:jqwik:1.8.1"`
