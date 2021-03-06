/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.containerpool.logging.test

import java.time.Instant

import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestProbe
import akka.util.ByteString
import common.{StreamLogging, WskActorSystem}
import org.scalatest.Matchers
import spray.json._
import whisk.common.TransactionId
import whisk.core.containerpool.logging.{DockerToActivationFileLogStore, LogLine}
import whisk.core.entity._

/**
 * Includes the tests for the DockerToActivationLogStore since the behavior towards the activation storage should
 * remain exactly the same.
 */
class DockerToActivationFileLogStoreTests
    extends DockerToActivationLogStoreTests
    with Matchers
    with WskActorSystem
    with StreamLogging {

  override def createStore() = new TestLogStoreTo(Sink.ignore)

  def toLoggedEvent(line: LogLine, userId: UUID, activationId: ActivationId, actionName: FullyQualifiedEntityName) = {
    val event = line.toJson.compactPrint
    val concatenated =
      s""","activationId":"${activationId.asString}","action":"${actionName.asString}","userId":"${userId.asString}""""

    event.dropRight(1) ++ concatenated ++ "}\n"
  }

  behavior of "DockerCouchDbFileLogStore"

  it should "read logs returned by the container,in mem and enrich + write them to the provided sink" in {
    val logs = List(LogLine(Instant.now.toString, "stdout", "this is just a test"))

    val testSource: Source[ByteString, _] = Source(logs.map(line => ByteString(line.toJson.compactPrint)))

    val testActor = TestProbe()

    val container = new TestContainer(testSource)
    val store = new TestLogStoreTo(Flow[ByteString].map(_.utf8String).to(Sink.actorRef(testActor.ref, ())))

    val collected = store.collectLogs(TransactionId.testing, user, activation, container, action)

    await(collected) shouldBe ActivationLogs(logs.map(_.toFormattedString).toVector)
    logs.foreach { line =>
      testActor.expectMsg(
        toLoggedEvent(line, user.authkey.uuid, activation.activationId, action.fullyQualifiedName(false)))
    }

    // Last message should be the full activation
    testActor.expectMsg(activation.toJson.compactPrint + "\n")
  }

  class TestLogStoreTo(override val writeToFile: Sink[ByteString, _])
      extends DockerToActivationFileLogStore(actorSystem)
}
