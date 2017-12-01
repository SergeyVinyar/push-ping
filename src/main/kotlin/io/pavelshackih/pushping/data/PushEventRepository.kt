package io.pavelshackih.pushping.data

import io.pavelshackih.pushping.model.PushEvent
import io.pavelshackih.pushping.model.PushEventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.stereotype.Repository

@Repository
@RepositoryRestResource(collectionResourceRel = "push", path = "push")
interface PushEventRepository : JpaRepository<PushEvent, Long> {

    fun findByMessageId(messageId: String): List<PushEvent>

    fun findByEventType(eventType: PushEventType): List<PushEvent>
}
