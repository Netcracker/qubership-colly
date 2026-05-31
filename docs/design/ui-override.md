# UI Override

## Концепция

UI Override - это механизм переопределения параметров Effective Set через UI, который:

- Применяется Calculator при генерации Effective Set
- Имеет приоритет выше ParamSet'ов и Predefined parameters, но ниже Custom Params
- Поддерживает контексты Effective Set: deployment, runtime, pipeline
- Хранится в трех файлах (по одному на контекст)

### UI Override Object

Объект для переопределения параметров Effective Set через UI.

**Расположение в репозитории:**

```text
...
└── environments
    └── <cluster>
        └── <environment>
            └── ui-overrides
                ├── deployment.yaml
                ├── runtime.yaml
                └── pipeline.yaml
```

**Структура объекта:**

```yaml
# Environment уровень (применяется ко всем namespace и application)
environment: hashmap
# Namespace уровень (применяется ко всем application в namespace)
namespaces:
  <deployPostfix>: hashmap
# Application уровень (применяется к конкретному application)
applications:
  <deployPostfix>:
    <applicationName>: hashmap
```

**Пример: `ui-overrides/deployment.yaml`**

```yaml
environment:
  param_env1: value1
  param_env2: value2
namespaces:
  deployPostfix-01:
    param_ns1: value1
    param_ns2: value2
  namespace-02:
    param_ns3: value3
applications:
  deployPostfix-01:
    app-01:
      param1: value2
      param4: null        # Удаление параметра
    app-02:
      param5: value5
  namespace-02:
    app-03:
      param6: value6
```

**Пример: `ui-overrides/pipeline.yaml`**

```yaml
environment:
  pipeline_param1: value1
  pipeline_param2: value2

namespaces:
  deployPostfix-01: (если нужны namespace-specific параметры для pipeline)
    pipeline_ns_param1: value3
```

### UI Override Original values

Часть ES, в которых Calculator сохраняет в Git значения тех параметров, которые пользователь переопределяет через UI Override: для каждого такого параметра фиксируется значение, которое он имел бы в Effective Set до применения UI Override.

Расположение в репозитории:

```text
...
└── environments
    └── <cluster>
        └── <environment>
            └── effective-set
                └── ui-override-original-values
                    ├── deployment.yaml
                    ├── runtime.yaml
                    └── pipeline.yaml
```

Структура объекта

```yaml
environment: hashmap
namespaces:
  <deployPostfix>: hashmap
applications:
  <deployPostfix>:
    <applicationName>: hashmap
```

## Роли Calculator и Colly

### Calculator

**Инпуты:**

1. те что сейчас
2. **UI Override Object** - Calculator ищет UI Override файлы по контрактному пути `<envs-path>/ui-overrides/` (опционально, если директория не существует - пропускается)

**Что делает:**

1. Генерирует Effective Set (как и сейчас)
2. Если UI Override Object существует - применяет оверрайды и сохраняет **UI Override Original values** в каталоге `effective-set/ui-override-original-values/`.

**Приоритет мержа (низкий → высокий):**

```text
...
- UI Override (environment level)      ← НОВОЕ (опционально)
- UI Override (namespace level)        ← НОВОЕ (опционально)
- UI Override (application level)      ← НОВОЕ (опционально)
- Custom Params (--custom-params)     ← highest priority
```

**Аутпут в Git (UI Override Original values):**

```text
effective-set/
  ui-override-original-values/
    deployment.yaml
    runtime.yaml
    pipeline.yaml
```

```yaml
environment: hashmap
namespaces:
  <deployPostfix>: hashmap
applications:
  <deployPostfix>:
    <applicationName>: hashmap
```

Calculator автоматически ищет UI Override файлы по пути `<envs-path>/ui-overrides/`. Если директория существует - применяет оверрайды, если нет - пропускает этот шаг.

### Colly

**Читает из Git/кэша:**

1. Effective Set (артефакты в `effective-set/`)
2. **UI Override Original values** - файлы в `effective-set/ui-override-original-values/`
3. **UI Override** - файлы в `ui-overrides/`

**Возвращает через API:**

1. Effective Set
2. UI Override

**Вычисляет для каждого параметра Effective Set:**

#### 1. `originalValue`

Значение параметра до применения UI Override:

- Если параметр есть в соответствующем файле **UI Override Original values** (`deployment.yaml` / `runtime.yaml` / `pipeline.yaml`) → берем значение оттуда
- Иначе → берем значение из текущего Effective Set (параметр не переопределялся через UI Override в сохранённых артефактах)

#### 2. `state`

Состояние параметра относительно UI Override:

- `ui_override_uncommitted` - параметр изменен в UI, но не закоммичен
- `ui_override_committed` - параметр закоммичен в Git в UI Override
- `ui_override_untouched` - параметр отсутствует в `request.parameters` и UI Override

#### 3. `value`

Целевое значение параметра:

- Если параметр изменен в UI (есть в `request.parameters`) → берем значение из `request.parameters`
- Иначе, если параметр есть в `ui-overrides/*.yaml` → берем значение из UI Override
- Иначе → берем значение из Effective Set

**Отдает в API response:**

- `value` - целевое значение (что будет в Effective Set после коммита)
- `state` - `ui_override_untouched` | `ui_override_uncommitted` | `ui_override_committed`
- `originalValue` - значение до UI Override

## Реализация

Calculator

1. Реализовать поиск UI Override файлов по контрактному пути `<envs-path>/ui-overrides/`:
   - `deployment.yaml` для deployment контекста
   - `runtime.yaml` для runtime контекста
   - `pipeline.yaml` для pipeline контекста
2. Если директория `ui-overrides/` не существует - пропустить этот шаг (UI Override опционален)
3. Если UI Override файлы найдены:
   1. Применять UI Override с приоритетом ниже --custom-params
   2. Генерировать каталог `effective-set/ui-override-original-values/` с файлами `deployment.yaml`, `runtime.yaml`, `pipeline.yaml` (запоминать originalValue перед применением UI Override)

Colly

1. Читать файлы UI Override из Git (`ui-overrides/deployment.yaml`, `runtime.yaml`, `pipeline.yaml`)
   1. Парсить структуру `environment` / `namespaces` / `applications`
2. Читать UI Override Original values из `effective-set/ui-override-original-values/`
3. Вычислять state на основе request.parameters, UI Override, UI_OVERRIDE_ORIGINAL_VALUES
4. Вычислять value и originalValue
5. API для коммита request.parameters → UI Override файлы в Git

Подробнее см.:

- [colly-effective-set-api.md](./colly-effective-set-api.md) - API для работы с Effective Set
- [colly-ui-parameters-api.md](./colly-ui-parameters-api.md) - API для работы с файлами UI Override
- [colly-versioning-conflicts.md](./colly-versioning-conflicts.md) - Версионирование и конфликты
