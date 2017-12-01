package io.pavelshackih.pushping.data

import io.pavelshackih.pushping.model.Device
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.stereotype.Repository

@Repository
@RepositoryRestResource(collectionResourceRel = "device", path = "device")
interface DeviceRepository : JpaRepository<Device, Long> {

    fun findByToken(@Param("token") token: String): List<Device>

    fun findByName(@Param("name") name: String): List<Device>
}