package com.poppick.poppick.feature.popup.dataaccess.client

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

/** 외부 API(snake_case) 용 매퍼. 앱 전역 매퍼 설정은 그대로 두고 복제해서 쓴다. Map 키는 변환되지 않는다. */
internal fun JsonMapper.snakeCase(): JsonMapper =
    rebuild()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
