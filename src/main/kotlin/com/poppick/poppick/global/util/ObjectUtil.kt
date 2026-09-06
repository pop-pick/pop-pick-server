package com.poppick.poppick.global.util

import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

fun Any.toMultiValueMap(objectMapper: ObjectMapper): MultiValueMap<String, String> {
    val map: Map<String, String> = objectMapper.convertValue(this, object : TypeReference<Map<String, String>>() {})
    return LinkedMultiValueMap<String, String>().apply { setAll(map) }
}
