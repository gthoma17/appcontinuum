package test.barinek.continuum

import io.barinek.continuum.TestDataSourceConfig
import io.barinek.continuum.restsupport.RestTemplate
import org.apache.http.message.BasicNameValuePair
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class FlowTest {
    val dataSource = TestDataSourceConfig() // cleans the database
    val template = RestTemplate()

    lateinit var allocations: Process
    lateinit var backlog: Process
    lateinit var registration: Process
    lateinit var timesheets: Process

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir")

        allocations = runCommand(8881, "java -jar $userDir/../allocations-server/build/libs/allocations-server-1.0-SNAPSHOT.jar", File(userDir))
        backlog = runCommand(8882, "java -jar $userDir/../backlog-server/build/libs/backlog-server-1.0-SNAPSHOT.jar", File(userDir))
        registration = runCommand(8883, "java -jar $userDir/../registration-server/build/libs/registration-server-1.0-SNAPSHOT.jar", File(userDir))
        timesheets = runCommand(8884, "java -jar $userDir/../timesheets-server/build/libs/timesheets-server-1.0-SNAPSHOT.jar", File(userDir))
    }

    @After
    fun tearDown() {
        allocations.destroy()
        backlog.destroy()
        registration.destroy()
        timesheets.destroy()
    }

    @Test
    fun testBasicFlow() {
        Thread.sleep(8000) // sorry, waiting for servers to start

        var response: String?

        val registrationServer = "http://localhost:8883"

        response = template.get(registrationServer, "application/json")
        assertEquals("Noop!", response)

        response = template.post("$registrationServer/registration", "application/json", """{"name": "aUser"}""")
        val aUserId = findResponseId(response)
        assert(aUserId.toLong() > 0)

        response = template.get("$registrationServer/users", "application/json", BasicNameValuePair("userId", aUserId))
        assert(!response.isNullOrEmpty())

        response = template.get("$registrationServer/accounts", "application/json", BasicNameValuePair("ownerId", aUserId))
        val anAccountId = findResponseId(response)
        assert(anAccountId.toLong() > 0)

        response = template.post("$registrationServer/projects", "application/vnd.appcontinuum.v2+json", """{"accountId":"$anAccountId","name":"aProject","active":true,"funded":true}""")
        val aProjectId = findResponseId(response)
        assert(aProjectId.toLong() > 0)

        response = template.get("$registrationServer/projects", "application/vnd.appcontinuum.v2+json", BasicNameValuePair("accountId", anAccountId))
        assert(!response.isNullOrEmpty())

        ///

        val allocationsServer = "http://localhost:8881"

        response = template.get(allocationsServer, "application/json")
        assertEquals("Noop!", response)

        response = template.post("$allocationsServer/allocations", "application/json", """{"projectId":$aProjectId,"userId":$aUserId,"firstDay":"2015-05-17","lastDay":"2015-05-26"}""")
        val anAllocationId = findResponseId(response)
        assert(aProjectId.toLong() > 0)

        response = template.get("$allocationsServer/allocations", "application/json", BasicNameValuePair("projectId", aProjectId))
        assert(!response.isNullOrEmpty())


        val backlogServer = "http://localhost:8882"

        response = template.get(backlogServer, "application/json")
        assertEquals("Noop!", response)

        response = template.post("$backlogServer/stories", "application/json", """{"projectId":$aProjectId,"name":"A story"}""")
        val aStoryId = findResponseId(response)
        assert(aStoryId.toLong() > 0)

        response = template.get("$backlogServer/stories", "application/json", BasicNameValuePair("projectId", aProjectId))
        assert(!response.isNullOrEmpty())


        val timesheetsServer = "http://localhost:8884"

        response = template.get(timesheetsServer, "application/json")
        assertEquals("Noop!", response)

        response = template.post("$timesheetsServer/time-entries", "application/json", """{"projectId":$aProjectId,"userId":$aUserId,"date":"2015-05-17","hours":"8"}""")
        val aTimeEntryId = findResponseId(response)
        assert(aTimeEntryId.toLong() > 0)

        response = template.get("$timesheetsServer/time-entries", "application/json", BasicNameValuePair("userId", aUserId))
        assert(!response.isNullOrEmpty())
    }

    /// Test Support

    private fun findResponseId(response: String) = Regex("id\":(\\d+),").find(response)?.groupValues!![1]

    private fun runCommand(port: Int, command: String, workingDir: File): Process {
        val builder = ProcessBuilder(*command.split(" ").toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.environment()["PORT"] = port.toString()
        return builder.start()
    }
}

