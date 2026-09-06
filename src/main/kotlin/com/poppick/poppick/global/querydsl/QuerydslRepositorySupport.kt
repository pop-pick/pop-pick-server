package com.poppick.poppick.global.querydsl

import com.poppick.poppick.global.paging.Cursorable
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Expression
import com.querydsl.core.types.dsl.PathBuilder
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.querydsl.jpa.impl.JPAUpdateClause
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.data.jpa.repository.support.Querydsl
import org.springframework.data.querydsl.SimpleEntityPathResolver
import kotlin.reflect.KClass

abstract class QuerydslRepositorySupport(
    private val entityClass: KClass<*>,
) {
    private lateinit var querydsl: Querydsl
    private lateinit var entityManager: EntityManager
    private lateinit var jpaQueryFactory: JPAQueryFactory

    @PersistenceContext
    fun setEntityManager(entityManager: EntityManager) {
        this.entityManager = entityManager

        val entityInformation = JpaEntityInformationSupport.getEntityInformation(entityClass.java, entityManager)
        val path = SimpleEntityPathResolver.INSTANCE.createPath(entityInformation.javaType)
        val builder = PathBuilder(path.type, path.metadata)

        querydsl = Querydsl(entityManager, builder)
        jpaQueryFactory = JPAQueryFactory(entityManager)
    }

    protected fun <T> select(select: EntityPath<T>): JPAQuery<T> = jpaQueryFactory.select(select)

    protected fun <T> select(select: Expression<T>): JPAQuery<T> = jpaQueryFactory.select(select)

    protected fun <T> selectFrom(from: EntityPath<T>): JPAQuery<T> = jpaQueryFactory.selectFrom(from)

    protected fun selectOne(): JPAQuery<Int> = jpaQueryFactory.selectOne()

    protected fun <T : Any> update(from: EntityPath<T>): JPAUpdateClause = jpaQueryFactory.update(from)

    protected fun <T> hasNext(
        cursorable: Cursorable<*>,
        content: MutableList<T>,
    ) = if (content.size > cursorable.limit) {
        content.removeLast()
        true
    } else {
        false
    }

    fun flush() = entityManager.flush()

    fun clear() = entityManager.clear()
}
