# Effective Set API для Colly

- [Effective Set API для Colly](#effective-set-api-для-colly)
  - [Введение](#введение)
  - [Контекст](#контекст)
    - [Effective Set](#effective-set)
    - [UI Override](#ui-override)
    - [UI Override Original values](#ui-override-original-values)
    - [`state`](#state)
    - [`value`](#value)
    - [`originalValue`](#originalvalue)
    - [Правила мержа параметров](#правила-мержа-параметров)
  - [Детальное описание API](#детальное-описание-api)
    - [POST /api/v1/environments/{environmentId}/effective-set](#post-apiv1environmentsenvironmentideffective-set)

## Введение

Данный документ содержит описание Effective Set API для Colly. Этот API используется для получения эффективного набора параметров (Effective Set) с метаданными о состоянии каждого параметра.

Для понимания концепции UI Override см. [ui-override.md](./ui-override.md).

Для работы с параметрами UI Override в Git см. [colly-ui-parameters-api.md](./colly-ui-parameters-api.md).

## Контекст

### Effective Set

Effective Set - это финальный набор параметров для Environment, сгенерированный Calculator слиянием всех источников параметров (template, inventory, custom params, UI Override).

Структура каталогов и контексты описаны в [calculator-cli.md](https://github.com/Netcracker/qubership-envgene/blob/main/docs/features/calculator-cli.md#version-20-effective-set-structure).

### UI Override

Объект для переопределения параметров Effective Set через UI.

Расположение в репозитории:

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

Структура объекта:

```yaml
environment: hashmap
namespaces:
  <deployPostfix>: hashmap
applications:
  <deployPostfix>:
    <applicationName>: hashmap
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

### `state`

Атрибут каждого параметра Effective Set в ответе `POST /api/v1/environments/{environmentId}/effective-set`.

Параметр может иметь одно из трех состояний:

1. `ui_override_untouched` - Я не изменял этот параметр в UI
   - key/value не задан через UI
   - key/value нет в файлах UI Override в Git
   - Параметр может быть или не быть в Effective Set

2. `ui_override_uncommitted` - Я изменил этот параметр в UI, но не закоммитил изменения
   - key/value задан в UI
   - key/value **нет** в файлах UI Override в Git
   - Значение применяется только локально в UI

3. `ui_override_committed` - Я изменил этот параметр в UI и закоммитил
   - key/value задан в UI
   - key/value **есть** в файлах UI Override в Git
   - Значение применено в Git и будет использовано Calculator при следующей генерации ES

### `value`

Атрибут каждого параметра Effective Set в ответе `POST /api/v1/environments/{environmentId}/effective-set`.

Целевое значение параметра - то, которое окажется в Effective Set после применения UI Override.

`value` формируется слиянием трёх источников данных в порядке приоритета (от низкого к высокому):

1. **Effective Set**
2. **UI Override из Git**
3. **Uncommitted параметры** (из запроса)

### `originalValue`

Атрибут каждого параметра Effective Set в ответе `POST /api/v1/environments/{environmentId}/effective-set`.

Оригинальное значение - то, которое было бы в Effective Set без учёта UI Override.

`originalValue` отвечает на вопрос, с какого значения пользователь отталкивался до изменений в UI.

**Источники `originalValue`:**

1. Файлы в `effective-set/ui-override-original-values/` (в зависимости от контекста запроса: `deployment.yaml`, `runtime.yaml` или `pipeline.yaml`).
2. Текущий Effective Set (если для параметра нет записи в этих файлах).

### Правила мержа параметров

При формировании Effective Set и определении значений параметров применяются рекурсивные правила мержа (recursive merge):

- **Для словарей**: рекурсивное объединение (если ключ есть в обоих источниках и значение — словарь, они объединяются рекурсивно)
- **Для списков**: полная замена (список из последнего файла заменяет предыдущий)
- **Для примитивов**: значение из последнего файла перезаписывает предыдущее

**Порядок приоритетов источников** (от низкого к высокому):

1. **Effective Set файлы** (базовые параметры)
2. **Файлы UI Override в Git** (закоммиченные изменения)
3. **Uncommitted параметры** (из запроса)

## Детальное описание API

### POST /api/v1/environments/{environmentId}/effective-set

Отдает эффективный набор параметров (Effective Set) с метаданными о состоянии каждого параметра для заданного контекста (deployment/runtime/pipeline).

**Параметры запроса:**

- `environmentId` (path, mandatory) - Environment uuid
- `context` (query, mandatory) - Контекст параметров: `deployment`, `runtime`, `pipeline`
- `namespaceName` (query, mandatory для deployment/runtime, не используется для pipeline) - Имя namespace
- `applicationName` (query, mandatory для deployment/runtime, не используется для pipeline) - Имя приложения

**Request Body:**

```json
{
  "parameters": {}
}
```

**Поля:**

- `parameters` (object, optional) - параметры из UI. В теле передаётся **полное** содержимое поля (включая уже закоммиченные в Git значения).

**Объектная модель EffectiveSetParameter:**

```yaml
## EffectiveSetParameter
_type: enum[container, leaf]
_data:
  value: any                     # Текущее значение параметра (после мержа всех источников)
  state: enum[                   # Состояние параметра с точки зрения пользователя UI
    ui_override_untouched,       # Состояние 1: параметр не изменялся через UI Override (untouched)
    ui_override_uncommitted,     # Состояние 2: задан в UI, но не закоммичен
    ui_override_committed        # Состояние 3: задан в UI, закоммичен
  ]
  originalValue: any             # Изначальное значение из Effective Set (до любого UI Override). Для ui_override_untouched равно текущему значению
```

> [!NOTE]
> Служебные поля `_type` и `_data` используют префикс подчеркивания для предотвращения конфликтов с пользовательскими именами параметров (например, если у пользователя есть параметр с именем `data` или `type`).

**Примеры запросов:**

**Deployment context (с незакоммиченными параметрами):**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/effective-set?context=deployment&namespaceName=env-01-core&applicationName=my-app
```

```json
{
  "parameters": {
    "CUSTOM_PARAM": "uncommitted-value",
    "backupDaemon": {
      "resources": {
        "limits": {
          "cpu": "400m"
        }
      }
    }
  }
}
```

**Pipeline context (без незакоммиченных параметров):**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters/effective-set?context=pipeline
```

```json
{}
```

**Runtime context:**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters/effective-set?context=runtime&namespaceName=env-01-core&applicationName=backend-service
```

```json
{
  "parameters": {
    "SERVICE_PORT": "9090"
  }
}
```

**Ответы:**

- `200 OK` - Effective Set успешно сформирован
  - Body: Effective Set с параметрами и метаданными
- `400 Bad Request` - Некорректные параметры запроса (отсутствует обязательный параметр, неверный контекст)
- `404 Not Found` - Environment, Namespace или Application не найдены

**Примеры ответов:**

> [!NOTE]
> Примеры ответов построены на основе следующей YAML структуры параметров из Effective Set:
>
> ```yaml
> backupDaemon:
>   data: true
>   backupSchedule: "0 0 * * *"
>   resources:
>     limits:
>       cpu: "300m"
> ```

**Пример 1: Deployment context с различными состояниями параметров:**

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "my-app",
  "parameters": {
    "backupDaemon": {
      "_type": "container",
      "_data": {
        "data": {
          "_type": "leaf",
          "_data": {
            "value": true,
            "state": "ui_override_untouched",
            "originalValue": true
          }
        },
        "backupSchedule": {
          "_type": "leaf",
          "_data": {
            "value": "0 0 * * *",
            "state": "ui_override_untouched",
            "originalValue": "0 0 * * *"
          }
        },
        "resources": {
            "_type": "container",
            "_data": {
            "limits": {
            "_type": "container",
            "_data": {
                "cpu": {
                  "_type": "leaf",
                  "_data": {
                    "value": "300m",
                    "state": "ui_override_untouched",
                    "originalValue": "300m"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

**Пример 2: Deployment context с незакоммиченными изменениями:**

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "my-app",
  "parameters": {
    "backupDaemon": {
      "_type": "container",
      "_data": {
        "data": {
          "_type": "leaf",
          "_data": {
            "value": false,
            "state": "ui_override_uncommitted",
            "originalValue": true
          }
        },
        "backupSchedule": {
          "_type": "leaf",
          "_data": {
            "value": "0 2 * * *",
            "state": "ui_override_uncommitted",
            "originalValue": "0 0 * * *"
          }
        },
        "resources": {
            "_type": "container",
            "_data": {
            "limits": {
            "_type": "container",
            "_data": {
                "cpu": {
                  "_type": "leaf",
                  "_data": {
                    "value": "500m",
                    "state": "ui_override_uncommitted",
                    "originalValue": "300m"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

**Пример 3: Deployment context с закоммиченными параметрами:**

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "my-app",
  "parameters": {
    "backupDaemon": {
      "_type": "container",
      "_data": {
        "data": {
          "_type": "leaf",
          "_data": {
            "value": true,
            "state": "ui_override_untouched",
            "originalValue": true
          }
        },
        "backupSchedule": {
          "_type": "leaf",
          "_data": {
            "value": "0 3 * * *",
            "state": "ui_override_committed",
            "originalValue": "0 0 * * *"
          }
        },
        "resources": {
            "_type": "container",
            "_data": {
            "limits": {
            "_type": "container",
            "_data": {
                "cpu": {
                  "_type": "leaf",
                  "_data": {
                    "value": "400m",
                    "state": "ui_override_committed",
                    "originalValue": "300m"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

**Пример 4: Пустой Effective Set (когда файлы отсутствуют):**

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "new-app",
  "parameters": {}
}
```

**Логика обработки:**

API работает с кэшированными данными Effective Set и файлами UI Override в Git.

При получении запроса:

1. **Валидация параметров запроса:**
   - Проверить наличие обязательных параметров (`environmentId`, `context`)
   - Для `deployment`/`runtime` контекстов: проверить наличие `namespaceName` и `applicationName`
   - Если параметры некорректны → `400 Bad Request`

2. **Определение `deployPostfix` (для deployment/runtime):**
   - Найти `Namespace` объект по `namespaceName` и `environmentId`
   - Получить значение поля `deployPostfix` из `Namespace` объекта
   - Если `deployPostfix` не найден → `404 Not Found`

3. **Чтение Effective Set из кэша:**

   Прочитать Effective Set из кэша. Кэш содержит результат мержа всех Effective Set файлов:

   - **Deployment context**: `deployment-parameters.yaml`, `credentials.yaml`, `collision-deployment-parameters.yaml`, `collision-credentials.yaml` (по возрастанию приоритета)
   - **Runtime context**: `parameters.yaml`, `credentials.yaml` (по возрастанию приоритета)
   - **Pipeline context**: `parameters.yaml`, `credentials.yaml` (по возрастанию приоритета)

   **Обработка credentials:**
   - SOPS метаданные (поле `sops`) удаляются из корня YAML
   - Если файл был зашифрован (поле `sops` присутствовало), все значения заменяются на `"*****"`

4. **Чтение файлов UI Override из кэша и их мерж:**

   Прочитать из кэша файлы (`ui-overrides/deployment.yaml`, `ui-overrides/runtime.yaml`, `ui-overrides/pipeline.yaml`) и применить мерж между уровнями:

   - **Deployment/Runtime context**: Environment → Namespace → Application (по возрастанию приоритета)
   - **Pipeline context**: Environment (мерж не нужен т.к. только один уровень)

   Если файл отсутствует → использовать пустой объект `{}`

5. **Чтение UI Override Original values из кэша:**

   Прочитать из кэша артефакты **UI Override Original values**, сгенерированные Calculator: `effective-set/ui-override-original-values/deployment.yaml`, `runtime.yaml`, `pipeline.yaml`.

   Если отсутствует нужный файл, либо для deployment/runtime нет ветки `<deployPostfix>` → `<applicationName>`, либо для pipeline нет соответствующих данных, считать фрагмент пустым и использовать `{}`.

6. **Определение состояния параметра (state):**

   Для каждого key/value определить состояние на основе его наличия в разных источниках:

   | Параметр в `parameters` (из запроса) | Параметр в UI Override (Git) | State                     |
   |--------------------------------------|------------------------------|---------------------------|
   | ❌ Нет                               | ❌ Нет                       | `ui_override_untouched`   |
   | ❌ Нет                               | ✅ Есть                      | `ui_override_committed`   |
   | ✅ Есть                              | ❌ Нет                       | `ui_override_uncommitted` |
   | ✅ Есть                              | ✅ Есть                      | `ui_override_committed`   |

   **Примечания:**
   - Uncommitted параметры из запроса (`parameters` в request body) имеют приоритет над committed
   - Если параметр есть и в uncommitted, и в Git → считается committed (значение из uncommitted будет в `value`, но state = `ui_override_committed`)

7. **Определение целевого значения параметра (`value`):**

   Целевое значение - то, которое окажется в Effective Set после применения UI Override.

   Порядок источников (от низкого приоритета к высокому):

   1. **Effective Set** (базовые параметры из кэша)
   2. **UI Override из Git** (закоммиченные изменения)
   3. **Uncommitted параметры** (из request body)

8. **Определение оригинального значения параметра (`originalValue`):**

   Для каждого параметра определить `originalValue` - то значение, которое было бы в Effective Set без учёта UI Override:

   - **Если для параметра есть запись** в соответствующем файле `ui-override-original-values/*.yaml` → взять значение оттуда
   - **Если записи нет**:
     - Если параметр в Effective Set → `originalValue` = значение из ES (из шага 3)
     - Если параметра нет в ES → `originalValue` = `null` (параметр добавлен через UI Override)

   **Примечания:**
   - `originalValue` **всегда присутствует** в ответе для каждого параметра
   - Для параметров со state `ui_override_untouched`: `originalValue` = `value` (равны, но оба указываются)
   - Для параметров со state `ui_override_uncommitted` или `ui_override_committed`: `originalValue` показывает значение до UI Override

9. **Формирование структуры ответа с метаданными:**

   Преобразовать каждый параметр в структуру `EffectiveSetParameter`:

   ```json
   {
     "_type": "leaf" | "container",
     "_data": {
       "value": <текущее значение>,
       "state": "ui_override_untouched" | "ui_override_uncommitted" | "ui_override_committed",
       "originalValue": <оригинальное значение>
     }
   }
   ```

   **Определение `_type`:**
   - `leaf` - если значение является примитивом (string, number, boolean, null) или списком
   - `container` - если значение является словарем (объектом)

   **Для container:**
   - Рекурсивно обработать все вложенные параметры
   - Каждый вложенный параметр также имеет `_type` и `_data`

10. **Возврат ответа:**
    - Вернуть сформированный Effective Set с контекстной информацией

**Особенности обработки:**

- Если Effective Set в Git отсутствует → возврат пустого `parameters: {}`
- Если файлы UI Override отсутствуют → используются только параметры из Effective Set
- Если `parameters` в запросе пустой или отсутствует → обрабатываются только закоммиченные параметры
- `originalValue` **всегда присутствует** для каждого параметра в ответе:
  - Для новых параметров (добавленных через UI Override) `originalValue` = `null`
  - Для untouched параметров `originalValue` = `value` (равны, но оба указываются)
  - Для uncommitted/committed параметров `originalValue` показывает значение до UI Override
- При обработке **не учитываются** параметры сервисного уровня, которые присутствуют только в deployment контексте:
  - Файл `deploy-descriptor.yaml`
  - Файлы в директории `per-service-parameters/`
