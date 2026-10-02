package com.poppick.poppick.feature.wish

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter
import com.poppick.poppick.global.util.KST
import com.poppick.poppick.security.jwt.JwtGenerator
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import kotlin.random.Random

/**
 * 로컬 전용 Postgres · Redis 에서 찜 등록(PUT /wish) 후 목록 · 인기 · 추천 · 상세의 wished 를 실제 보안 필터 · JWT 로 확인한다.
 * 찜한 회원은 찜한 팝업만 wished = true, 찜하지 않은 다른 회원 · 비회원은 모두 false, 추천은 비회원 401 이다.
 * **공유 DB 에는 절대 연결하지 않는다.** 실행 조건 · 준비는 PopupViewCountIntegrationTest 와 같고, Redis(POPUP_VIEW_TEST_REDIS_HOST)도 필요하다.
 *
 * 찜 저장의 ON CONFLICT (member_key, popup_id) 는 운영 DDL 의 유니크 인덱스가 필요한데 hbm2ddl 은 만들지 않으므로,
 * 시작할 때 이 로컬 DB 에만 같은 유니크 인덱스를 만든다.
 * 인기 Top3 를 이 테스트가 정하도록 다른 통합 테스트(최대 2_000_009)보다 큰 조회수를 주고, 끝나면 만든 데이터를 지운다.
 */
@EnabledIfEnvironmentVariables(
    EnabledIfEnvironmentVariable(named = "POPUP_VIEW_TEST_DB_URL", matches = ".+"),
    EnabledIfEnvironmentVariable(named = "POPUP_VIEW_TEST_REDIS_HOST", matches = ".+"),
)
@SpringBootTest(
    properties = [
        "collection.cron=-",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    ],
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopupWishedFlowIntegrationTest {
    companion object {
        private val LOCAL_HOSTS = listOf("//localhost:", "//127.0.0.1:")

        /** 다른 통합 테스트에서 쓰지 않는 카테고리 id. 두 회원의 관심 카테고리이자 세 팝업의 카테고리. */
        private const val CATEGORY = 9401

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            val url = System.getenv("POPUP_VIEW_TEST_DB_URL")
            check(LOCAL_HOSTS.any { url.contains(it) }) { "로컬 DB(localhost · 127.0.0.1)만 허용한다: $url" }
            registry.add("spring.datasource.hikari.jdbc-url") { url }
            registry.add("spring.datasource.hikari.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.datasource.hikari.username") { System.getenv("POPUP_VIEW_TEST_DB_USERNAME") ?: "postgres" }
            registry.add("spring.datasource.hikari.password") { System.getenv("POPUP_VIEW_TEST_DB_PASSWORD") ?: "" }
            System.getenv("POPUP_VIEW_TEST_REDIS_HOST")?.let { host -> registry.add("spring.data.redis.host") { host } }
        }
    }

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var popupWriter: PopupWriter

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var jwtGenerator: JwtGenerator

    private lateinit var mockMvc: MockMvc

    private val today = LocalDate.now(KST)
    private val marker = "wishflow${Random.nextInt(100_000, 999_999)}"
    private val ids = mutableMapOf<String, Long>()
    private val memberKeys = mutableListOf<String>()

    private lateinit var wisherToken: String
    private lateinit var otherToken: String

    @BeforeAll
    fun seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()

        jdbcTemplate.execute("ALTER TABLE popup ALTER COLUMN view_count SET DEFAULT 0")
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_popup_wish_member_popup ON popup_wish (member_key, popup_id)")

        fun seed(
            name: String,
            viewCount: Long,
        ) {
            val id =
                popupWriter
                    .save(
                        Popup(
                            source = SourceType.PERPLEXITY,
                            title = "$marker $name",
                            startDate = today.minusDays(1),
                            endDate = today.plusDays(10),
                            interestCategoryId = CATEGORY,
                        ),
                    ).id!!
            jdbcTemplate.update("UPDATE popup SET view_count = ? WHERE popup_id = ?", viewCount, id)
            ids[name] = id
        }

        // 인기 Top3 = first, second, third. 두 회원의 관심 카테고리와도 맞아 추천 결과도 같은 세 팝업이다.
        seed("first", viewCount = 3_000_003)
        seed("second", viewCount = 3_000_002)
        seed("third", viewCount = 3_000_001)

        wisherToken = token(member("wisher"))
        otherToken = token(member("other"))

        // 찜 등록은 실제 API 로 한다: wisher 가 first · third 를 찜한다(second 는 찜하지 않는다).
        listOf("first", "third").forEach { name ->
            mockMvc
                .perform(put("/api/v1/popups/${ids.getValue(name)}/wish").bearer(wisherToken))
                .andReturn()
                .response
                .status shouldBe 204
        }
    }

    @AfterAll
    fun cleanUp() {
        memberKeys.forEach { memberKey ->
            jdbcTemplate.update("DELETE FROM popup_wish WHERE member_key = ?", memberKey)
            jdbcTemplate.update("DELETE FROM member_interest_category WHERE member_key = ?", memberKey)
            jdbcTemplate.update(
                "DELETE FROM member_roles WHERE member_id IN (SELECT member_id FROM member WHERE member_key = ?)",
                memberKey,
            )
            jdbcTemplate.update("DELETE FROM member WHERE member_key = ?", memberKey)
        }
        ids.values.forEach { jdbcTemplate.update("DELETE FROM popup WHERE popup_id = ?", it) }
    }

    /** 회원 저장 + 관심 카테고리(CATEGORY). JWT 필터가 memberKey 로 회원을 찾는다. */
    private fun member(name: String): String {
        val memberKey = "$marker-$name"
        memberRepository.saveAndFlush(MemberEntity(memberKey = memberKey, email = "$memberKey@example.com"))
        jdbcTemplate.update("INSERT INTO member_interest_category (member_key, interest_category_id) VALUES (?, ?)", memberKey, CATEGORY)
        memberKeys += memberKey
        return memberKey
    }

    private fun token(memberKey: String) = jwtGenerator.generateJwt(memberKey).accessToken

    private fun MockHttpServletRequestBuilder.bearer(token: String?) =
        apply { token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") } }

    private fun getJson(
        path: String,
        token: String?,
        expectedStatus: Int = 200,
    ): JsonNode {
        val response = mockMvc.perform(get(path).bearer(token)).andReturn().response
        response.status shouldBe expectedStatus
        return Fixtures.jsonMapper.readTree(response.getContentAsString(Charsets.UTF_8))
    }

    /** 응답 카드 배열을 이 테스트 팝업 이름 → wished 로 바꾼다(다른 팝업은 무시). */
    private fun JsonNode.wishedByName(): Map<String, Boolean> {
        val names = ids.entries.associate { (name, id) -> id to name }
        return toList()
            .mapNotNull { card -> names[card["popupId"].asLong()]?.let { it to card["wished"].asBoolean() } }
            .toMap()
    }

    private val wisherExpected = mapOf("first" to true, "second" to false, "third" to true)
    private val nobodyExpected = mapOf("first" to false, "second" to false, "third" to false)

    @Test
    fun `목록 - 찜한 회원은 찜한 팝업만 true, 다른 회원 · 비회원은 모두 false`() {
        val path = "/api/v1/popups?keyword=$marker&sort=popular"

        getJson(path, wisherToken)["data"]["content"].wishedByName() shouldBe wisherExpected
        getJson(path, otherToken)["data"]["content"].wishedByName() shouldBe nobodyExpected
        getJson(path, null)["data"]["content"].wishedByName() shouldBe nobodyExpected
    }

    @Test
    fun `인기 - 이 테스트 팝업이 인기 Top3 이고, 찜한 회원은 찜한 팝업만 true, 다른 회원 · 비회원은 모두 false`() {
        val wisher = getJson("/api/v1/popups/popular", wisherToken)["data"]

        wisher.toList().map { it["popupId"].asLong() } shouldBe listOf("first", "second", "third").map { ids.getValue(it) }
        wisher.wishedByName() shouldBe wisherExpected
        getJson("/api/v1/popups/popular", otherToken)["data"].wishedByName() shouldBe nobodyExpected
        getJson("/api/v1/popups/popular", null)["data"].wishedByName() shouldBe nobodyExpected
    }

    @Test
    fun `추천 - 찜한 회원은 찜한 팝업만 true, 다른 회원은 모두 false, 비회원은 401 · E1000`() {
        val wisher = getJson("/api/v1/popups/recommended", wisherToken)["data"]

        wisher.toList().map { it["popupId"].asLong() } shouldBe listOf("first", "second", "third").map { ids.getValue(it) }
        wisher.wishedByName() shouldBe wisherExpected
        getJson("/api/v1/popups/recommended", otherToken)["data"].wishedByName() shouldBe nobodyExpected
        getJson("/api/v1/popups/recommended", null, expectedStatus = 401)["error"]["errorCode"].asString() shouldBe "E1000"
    }

    @Test
    fun `상세 - 찜한 회원은 찜한 팝업만 true, 다른 회원 · 비회원은 false 이고 viewCount 도 함께 내려간다`() {
        listOf("first", "second", "third").forEach { name ->
            val path = "/api/v1/popups/${ids.getValue(name)}"

            with(getJson(path, wisherToken)["data"]) {
                this["wished"].asBoolean() shouldBe wisherExpected.getValue(name)
                this["viewCount"].isNumber shouldBe true
            }
            getJson(path, otherToken)["data"]["wished"].asBoolean() shouldBe false
            getJson(path, null)["data"]["wished"].asBoolean() shouldBe false
        }
    }

    @Test
    fun `내 찜 목록 - 찜한 회원은 찜한 두 팝업만, 다른 회원은 빈 목록, 비회원은 401`() {
        getJson("/api/v1/wishes", wisherToken)["data"]["content"].toList().map { it["popupId"].asLong() }.toSet() shouldBe
            setOf(ids.getValue("first"), ids.getValue("third"))
        getJson("/api/v1/wishes", otherToken)["data"]["content"].size() shouldBe 0
        getJson("/api/v1/wishes", null, expectedStatus = 401)
    }
}
