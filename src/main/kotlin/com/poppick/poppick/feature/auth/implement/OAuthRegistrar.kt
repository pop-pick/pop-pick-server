package com.poppick.poppick.feature.auth.implement

import com.poppick.poppick.feature.auth.dataaccess.entity.OAuthEntity
import com.poppick.poppick.feature.auth.dataaccess.repository.OAuthRepository
import com.poppick.poppick.feature.auth.domain.OAuthMember
import com.poppick.poppick.feature.member.implement.MemberRegistrar
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OAuthRegistrar(
    private val oAuthRepository: OAuthRepository,
    private val memberRegistrar: MemberRegistrar,
) {
    @Transactional
    fun registerIfNewAndGetMemberKey(oAuthMember: OAuthMember): String =
        oAuthRepository.findByAccountAndProvider(oAuthMember.account, oAuthMember.provider)?.memberKey
            ?: register(oAuthMember)

    private fun register(oAuthMember: OAuthMember): String {
        return memberRegistrar.register(oAuthMember.toNewMember()).also {
            oAuthRepository.save(OAuthEntity.of(oAuthMember, it))
        }
    }
}
