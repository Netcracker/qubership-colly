# UI Parameters API для Colly

- [UI Parameters API для Colly](#ui-parameters-api-для-colly)
  - [Введение](#введение)
  - [Non-functional Requirements](#non-functional-requirements)
  - [Контекст](#контекст)
    - [Структура файлов UI Override](#структура-файлов-ui-override)
    - [Расположение в репозитории](#расположение-в-репозитории)
  - [Общие правила API](#общие-правила-api)
    - [Определение уровня (level)](#определение-уровня-level)
    - [Поддержка контекстов по уровням](#поддержка-контекстов-по-уровням)
  - [Детальное описание API](#детальное-описание-api)
    - [GET /api/v1/environments/{environmentId}/ui-parameters](#get-apiv1environmentsenvironmentidui-parameters)
    - [POST /api/v1/environments/{environmentId}/ui-parameters](#post-apiv1environmentsenvironmentidui-parameters)
    - [DELETE /api/v1/environments/{environmentId}/ui-parameters](#delete-apiv1environmentsenvironmentidui-parameters)

## Введение

Данный документ содержит описание UI Parameters API для Colly. Этот API используется для управления параметрами UI Override в Effective Set через файлы в каталоге `ui-overrides/`.

Параметры UI Override создаются и изменяются только через Colly API.

## Non-functional Requirements

1. Время ответа на `GET /api/v1/environments/{environmentId}/ui-parameters` должно быть менее 300 мс
2. Валидации `commitMessage` в `POST /api/v1/environments/{environmentId}/ui-parameters` со стороны Colly быть не должно (валидация на стороне UI)
3. `POST /api/v1/environments/{environmentId}/ui-parameters` должны быть транзакционными

## Контекст

### Структура файлов UI Override

Файлы UI Override (`deployment.yaml`, `runtime.yaml`, `pipeline.yaml`) хранятся в YAML с тремя уровнями параметров:

- **Environment level** - параметры применяются ко всем namespace и application в окружении
- **Namespace level** - параметры применяются ко всем application в namespace
- **Application level** - параметры применяются к конкретному application

```yaml
environment: hashmap
namespaces:
  <deployPostfix>: hashmap
applications:
  <deployPostfix>:
    <applicationName>: hashmap
```

**Пример структуры для deployment контекста:**

```yaml
# Environment уровень
environment:
  param_env1: value1
  param_env2: value2
# Namespace уровень
namespaces:
  namespace-01:
    param_ns1: value1
    param_ns2: value2
  namespace-02:namespace "test-core")
    param_ns3: value3
# Application уровень
applications:
  namespace-01:
    app-01:               # applicationName
      param1: value2
      param4: null        # Удаление параметра
    app-02:
      param5: value5
  namespace-02:
    app-03:
      param6: value6
```

**Для runtime контекста:**

```yaml
environment:
  runtime_env_param1: value1
namespaces:
  namespace-01:
    runtime_ns_param1: value2
applications:
  namespace-01:
    app-01:      # applicationName
      runtime_param1: value1
      runtime_param2: value2
```

**Для pipeline контекста:**

```yaml
environment:
  pipeline_param1: value1
  pipeline_param2: value2
namespaces:
  namespace-01:
    pipeline_ns_param1: value3
```

### Расположение в репозитории

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

## Общие правила API

### Определение уровня (level)

Уровень определяется автоматически на основе переданных query параметров:

- Если не переданы `namespaceName` и `applicationName` → **Environment level**
- Если передан только `namespaceName` → **Namespace level**  
- Если переданы оба параметра → **Application level**

### Поддержка контекстов по уровням

- **Environment Level:** `deployment`, `runtime`, `pipeline`
- **Namespace Level:** `deployment`, `runtime` (pipeline не поддерживается)
- **Application Level:** `deployment`, `runtime` (pipeline не поддерживается)

## Детальное описание API

### GET /api/v1/environments/{environmentId}/ui-parameters

Получить параметры UI Override для окружения.

> [!IMPORTANT]
> API работает с кэшированными данными. Colly не обращается к Git репозиторию напрямую при каждом GET запросе. Кэш создается и обновляется по расписанию.

**Параметры запроса:**

- `environmentId` (path, mandatory) - Environment uuid
- `namespaceName` (query, optional) - Имя namespace (для Namespace/Application уровня)
- `applicationName` (query, optional) - Имя приложения (для Application уровня)

**Примеры запросов:**

```text
# Environment Level
GET /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters

# Namespace Level
GET /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters?namespaceName=env-01-core

# Application Level
GET /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters?namespaceName=env-01-core&applicationName=my-app
```

**Ответы:**

- `200 OK` - Успешный запрос (всегда возвращается, даже если файлы UI Override не существуют)
  - Body: `map` (структура зависит от уровня, см. ниже)
- `404 Not Found` - Environment, Namespace или Application не найден

**Пример успешного ответа для Environment Level:**

```json
{
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "pipeline": {
      "KEY1": "value1",
      "KEY2": "value2"
    }
  }
}
```

**Пример успешного ответа для Namespace/Application Level:**

```json
{
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY1": "value1",
      "KEY2": "value2"
    }
  }
}
```

**Логика обработки:**

API работает с кэшированными данными файлов UI Override. Кэш создается и обновляется по расписанию.

При получении запроса:

1. **Определить уровень (level)** (см. раздел ["Определение уровня"](#определение-уровня-level))

2. **Для Namespace/Application level - получить `deployPostfix`:**
   - Найти `Namespace` объект по `namespaceName` и `environmentId`
   - Получить значение поля `deployPostfix` из `Namespace` объекта
   - Если `deployPostfix` не найден → `404 Not Found`

3. **Получить параметры из кэша:**
   - Для каждого контекста прочитать кэшированные данные из соответствующего файла UI Override:
     - `deployment` → кэш `ui-overrides/deployment.yaml`
     - `runtime` → кэш `ui-overrides/runtime.yaml`
     - `pipeline` → кэш `ui-overrides/pipeline.yaml`
   - Извлечь параметры для соответствующего уровня:
     - **Environment level**: из секции `environment`
     - **Namespace level**: из секции `namespaces[deployPostfix]`
     - **Application level**: из секции `applications[deployPostfix][applicationName]`
   - Если кэш для файла отсутствует → возврат пустых параметров `{}` для этого контекста с warning в логах

**Кэширование:**

Colly кэширует файлы UI Override из Git репозитория. API работает исключительно с кэшированными данными, не читает файлы из репозитория напрямую при каждом GET запросе.

**Создание и обновление кэша:**

- **По расписанию:**
  - Кэш обновляется автоматически каждые n минут для всех окружений
  - Процесс выполняется в фоне, независимо от API запросов

- **Процесс создания кэша:**
  1. Прочитать файлы UI Override из Git: `ui-overrides/deployment.yaml`, `ui-overrides/runtime.yaml`, `ui-overrides/pipeline.yaml`
  2. Если файл не найден, кэш для этого контекста не создается
  3. Валидация структуры файла:
     - Проверить наличие обязательных секций: `environment`, `namespaces`, `applications`
  4. Если валидация успешна → создать/обновить кэш

**Инвалидация кэша:**

- **При кэшировании по расписанию:**
  - При ошибках валидации файлов UI Override
  - При отсутствии обязательных секций
  - Действие: кэш для данного контекста удаляется, warning в логах
  - При следующем periodic sync файл будет прочитан и провалидирован заново
  - При GET запросе невалидный файл рассматривается как отсутствующий → возврат пустого объекта `{}` для соответствующего контекста

**Примечания:**

- `deployment` - обязательный в ответе, по умолчанию `{}`
- `runtime` - обязательный в ответе, по умолчанию `{}`
- `pipeline`
  - обязательный в ответе только для Environment Level, по умолчанию `{}`
  - не включается в ответ для Namespace и Application Level
- `404 Not Found` возвращается только если не найден сам Environment, Namespace или Application (не файлы UI Override)
- Если файл UI Override не существует, соответствующий контекст возвращается `{}`

### POST /api/v1/environments/{environmentId}/ui-parameters

Создать или обновить параметры UI Override.

**Параметры запроса:**

- `environmentId` (path) - Environment uuid
- `namespaceName` (query, optional) - Имя namespace (для Namespace/Application уровня)
- `applicationName` (query, optional) - Имя приложения (для Application уровня)

**Request Body:**

```json
{
  "commitMessage": "string",
  "commitUser": "string",
  "commitUserEmail": "string",
  "parameters": {
    "deployment": {},
    "runtime": {},
    "pipeline": {}
  }
}
```

**Поля:**

- `commitMessage` (string, mandatory) - Коммит мессадж для коммита в Git репозиторий
- `commitUser` (string, mandatory) - Имя пользователя под которым происходит коммит
- `commitUserEmail` (string, mandatory) - Email пользователя под которым происходит коммит
- `parameters` (object, mandatory) - Параметры для сохранения, содержит контексты `deployment`, `runtime`, `pipeline`

**Примеры запросов:**

**Environment Level:**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters
```

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com",
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "pipeline": {
      "KEY1": "value1",
      "KEY2": "value2"
    }
  }
}
```

**Namespace Level:**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters?namespaceName=env-01-core
```

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com",
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY1": "value1",
      "KEY2": "value2"
    }
  }
}
```

**Application Level:**

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters?namespaceName=env-01-core&applicationName=my-app
```

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com",
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY1": "value1",
      "KEY2": "value2"
    }
  }
}
```

**Ответы:**

- `200 OK` - Параметры обновлены (если файлы UI Override уже существовали)
  - Body: полный запрос (см. ниже)
- `201 Created` - Параметры созданы (если файлы UI Override ранее отсутствовали)
  - Body: полный запрос (см. ниже)
- `400 Bad Request` - Ошибка валидации (например, передан `pipeline` для Namespace/Application Level)
- `404 Not Found` - Environment, Namespace или Application не найден

**Примеры ответов:**

**Пример успешного ответа Environment Level:**

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com",
  "parameters": {
    "deployment": {
      "KEY1": "value1",
      "KEY2": "value2"
    },
    "runtime": {
      "KEY3": "value3"
    },
    "pipeline": {
      "KEY4": "value4"
    }
  }
}
```

**Пример успешного ответа Namespace/Application Level:**

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com",
  "parameters": {
    "deployment": {
      "NAMESPACE_PARAM": "namespace-value"
    },
    "runtime": {
      "RUNTIME_PARAM": "runtime-value"
    }
  }
}
```

**Логика обработки:**

1. **Валидация запроса:**
   - Проверить наличие обязательных полей: `commitMessage`, `commitUser`, `commitUserEmail`, `parameters`
   - Проверить существование Environment по `environmentId`
   - Если указан `namespaceName` → проверить существование Namespace и получить `deployPostfix`
   - Если указан `applicationName` → проверить, что указан `namespaceName` и проверить существование Application
   - Если валидация не прошла → `400 Bad Request` или `404 Not Found`

2. **Определить уровень** (см. раздел ["Определение уровня"](#определение-уровня-level))

3. **Валидация контекстов:**
   - Для Namespace/Application Level: если передан `pipeline` контекст → `400 Bad Request` с сообщением "Pipeline context is not supported for Namespace/Application level"

4. **Обновление файлов UI Override:**
   - Для каждого контекста из `parameters` (`deployment`, `runtime`, `pipeline`):
     - Прочитать соответствующий файл из Git репозитория: `ui-overrides/deployment.yaml`, `ui-overrides/runtime.yaml`, `ui-overrides/pipeline.yaml`
     - Если файл не существует - создать с пустыми секциями `environment`, `namespaces`, `applications`
     - Выполнить рекурсивный мерж (recursive merge) новых параметров в соответствующую секцию:
       - **Environment level**: в секцию `environment`
       - **Namespace level**: в секцию `namespaces[deployPostfix]`
       - **Application level**: в секцию `applications[deployPostfix][applicationName]`
     - Записать обновленный файл обратно в Git репозиторий

5. **Сохранение в Git:**
   - Создать один commit со всеми изменениями в репозитории
   - Использовать указанные `commitMessage`, `commitUser`, `commitUserEmail`
   - Push изменений в Git репозиторий
   - Если возникла ошибка при commit/push → см. [colly-versioning-conflicts.md](./colly-versioning-conflicts.md)

6. **Инвалидация кэша:**
   - Инвалидировать кэш файлов UI Override для данного Environment
   - Кэш будет обновлен при следующем periodic sync (обычно в течение нескольких минут)

   > [!NOTE]
   > GET запросы могут возвращать старые данные до момента обновления кэша.
   > Для получения гарантированно актуальных данных после POST используйте данные из ответа POST endpoint.

7. **Формирование ответа:**
   - Вернуть `200 OK` или `201 Created` с теми же данными, что были в запросе

### DELETE /api/v1/environments/{environmentId}/ui-parameters

Удалить **все** параметры UI Override для окружения. Используется в reset кейсе.

Удаляются все три файла UI Override (`deployment.yaml`, `runtime.yaml`, `pipeline.yaml`) из директории `ui-overrides/` для указанного environment.

Удаление является транзакционной операцией - либо все изменения применяются, либо ничего.

**Параметры запроса:**

- `environmentId` (path, mandatory) - Environment uuid

**Request Body:**

```json
{
  "commitMessage": "string",
  "commitUser": "string",
  "commitUserEmail": "string"
}
```

**Поля:**

- `commitMessage` (string, mandatory) - Коммит мессадж для коммита в Git репозиторий
- `commitUser` (string, mandatory) - Имя пользователя под которым происходит коммит
- `commitUserEmail` (string, mandatory) - Email пользователя под которым происходит коммит

**Пример запроса:**

```http
DELETE /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters
```

```json
{
  "commitMessage": "Reset all UI Override parameters for environment",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "Vasya@mail.com"
}
```

**Ответы:**

- `204 No Content` - Параметры успешно удалены
- `404 Not Found` - Environment не найден
- `400 Bad Request` - Ошибка валидации параметров запроса

**Логика обработки:**

1. **Валидация запроса:**
   - Проверить наличие обязательных полей: `commitMessage`, `commitUser`, `commitUserEmail`
   - Проверить существование Environment по `environmentId`
   - Если валидация не прошла → `400 Bad Request` или `404 Not Found`

2. **Удалить все файлы UI Override:**

   Для каждого контекста (`deployment`, `runtime`, `pipeline`):

   - Удалить соответствующий файл UI Override из Git репозитория:
     - `ui-overrides/deployment.yaml`
     - `ui-overrides/runtime.yaml`
     - `ui-overrides/pipeline.yaml`
   - Если файл не существует → пропустить

3. **Сохранение в Git:**
   - Создать один commit со всеми удалениями
   - Использовать переданные `commitMessage`, `commitUser`, `commitUserEmail` для коммита
   - Push изменений в Git репозиторий
   - Если возникла ошибка при commit/push → см. [colly-versioning-conflicts.md](./colly-versioning-conflicts.md)

4. **Инвалидация кэша:**
   - Инвалидировать кэш файлов UI Override для данного Environment
   - Следующий periodic sync обновит кэш с новыми изменениями из Git

5. **Формирование ответа:**
   - Вернуть `204 No Content`

**Особенности:**

- Если ни один файл UI Override не существовал → операция всё равно считается успешной → возврат `204 No Content`
- **Атомарность**: операция транзакционна - либо все изменения сохраняются, либо ничего
- Удаляются **все** параметры UI Override для environment (для всех уровней: Environment, Namespace, Application)
- После удаления все параметры UI Override сбрасываются, и Effective Set будет формироваться только из базовых источников
