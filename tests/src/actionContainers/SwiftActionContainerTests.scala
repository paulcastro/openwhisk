/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package actionContainers

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import ActionContainer.withContainer
import spray.json.DefaultJsonProtocol._
import spray.json._

import common.WskActorSystem

@RunWith(classOf[JUnitRunner])
class SwiftActionContainerTests extends BasicActionRunnerTests with WskActorSystem {

    // note: "out" will likely not be empty in some swift build as the compiler
    // prints status messages and there doesn't seem to be a way to quiet them
    val enforceEmptyOutputStream = true
    val enforceEmptyErrorStream = true

    lazy val swiftContainerImageName = "swiftaction"
    lazy val envCode = makeEnvCode("NSProcessInfo.processInfo()")

    def makeEnvCode(processInfo: String) = ("""
         |func main(args: [String: Any]) -> [String: Any] {
         |     let env = """+processInfo+""".environment
         |     var a = "???"
         |     var b = "???"
         |     var c = "???"
         |     var d = "???"
         |     var e = "???"
         |     var f = "???"
         |     if let v : String = env["__OW_API_HOST"] {
         |         a = "\(v)"
         |     }
         |     if let v : String = env["__OW_API_KEY"] {
         |         b = "\(v)"
         |     }
         |     if let v : String = env["__OW_NAMESPACE"] {
         |         c = "\(v)"
         |     }
         |     if let v : String = env["__OW_ACTION_NAME"] {
         |         d = "\(v)"
         |     }
         |     if let v : String = env["__OW_ACTIVATION_ID"] {
         |         e = "\(v)"
         |     }
         |     if let v : String = env["__OW_DEADLINE"] {
         |         f = "\(v)"
         |     }
         |     return ["api_host": a, "api_key": b, "namespace": c, "action_name": d, "activation_id": e, "deadline": f]
         |}
         """).stripMargin

    lazy val echoCode = """
         |func main(args: [String: Any]) -> [String: Any] {
         |     let stderr = NSFileHandle.fileHandleWithStandardError()
         |     print("hello stdout")
         |
         |     stderr.writeData("hello stderr".dataUsingEncoding(NSUTF8StringEncoding)!)
         |     return args
         |}
         """.stripMargin

    lazy val errorCode = """
                | // You need an indirection, or swiftc detects the div/0
                | // at compile-time. Smart.
                | func div(x: Int, _ y: Int) -> Int {
                |     return x/y
                | }
                | func main(args: [String: Any]) -> [String: Any] {
                |     return [ "divBy0": div(5,0) ]
                | }
            """.stripMargin

    // Helpers specific to swiftaction
    override def withActionContainer(env: Map[String, String] = Map.empty)(code: ActionContainer => Unit) = {
        withContainer(swiftContainerImageName, env)(code)
    }

    def fixture =
    new {
      val (out, err) = withActionContainer() { c =>
        val code = errorCode

        val (initCode, _) = c.init(initPayload(code))
        initCode should be(200)

        val (runCode, runRes) = c.run(runPayload(JsObject()))
        runCode should be(502)

        runRes shouldBe defined
        runRes.get.fields.get("error") shouldBe defined

      }
    }

    behavior of swiftContainerImageName

    // remove this test: it will not even compile under Swift 3 anymore
    // so it should not be possible to write an action that does not return
    // a [String:Any]
    /*testNotReturningJson(
        """
        |func main(args: [String: Any]) -> String {
        |    return "not a json object"
        |}
        """.stripMargin)
    */

    it should "return some error on action error" in {
        val (out, err) = withActionContainer() { c =>
            val code = errorCode

            val (initCode, _) = c.init(initPayload(code))
            initCode should be(200)

            val (runCode, runRes) = c.run(runPayload(JsObject()))
            runCode should be(502)

            runRes shouldBe defined
            runRes.get.fields.get("error") shouldBe defined
        }

        checkStreams(out, err, {
            case (o, e) =>
                if (enforceEmptyOutputStream) o shouldBe empty
                e shouldBe empty
        })
    }

    testEcho(Seq {
        ("swift", echoCode)
    })


    it should "support actions using non-default entry points" in {
        withActionContainer() { c =>
            val code = """
                | func niam(args: [String: Any]) -> [String: Any] {
                |   return [ "result": "it works" ]
                | }
                |""".stripMargin

            val (initCode, initRes) = c.init(initPayload(code, main = "niam"))
            initCode should be(200)

            val (_, runRes) = c.run(runPayload(JsObject()))
            runRes.get.fields.get("result") shouldBe Some(JsString("it works"))
        }
    }


    it should "log compilation errors" in {
        val (out, err) = withActionContainer() { c =>
            val code = """
              | 10 PRINT "Hello!"
              | 20 GOTO 10
            """.stripMargin

            val (initCode, _) = c.init(initPayload(code))
            initCode should not be (200)

            val (runCode, runRes) = c.run(runPayload(JsObject("basic" -> JsString("forever"))))
            runCode should be(502)
        }

        checkStreams(out, err, {
            case (o, e) =>
                if (enforceEmptyOutputStream) o shouldBe empty
                e.toLowerCase should include("error")
        })
    }

    it should "support application errors" in {
        val (out, err) = withActionContainer() { c =>
            val code = """
                | func main(args: [String: Any]) -> [String: Any] {
                |     return [ "error": "sorry" ]
                | }
            """.stripMargin

            val (initCode, _) = c.init(initPayload(code))
            initCode should be(200)

            val (runCode, runRes) = c.run(runPayload(JsObject()))
            runCode should be(200) // action writer returning an error is OK

            runRes shouldBe defined
            runRes should be(Some(JsObject("error" -> JsString("sorry"))))
        }

        checkStreams(out, err, {
            case (o, e) =>
                if (enforceEmptyOutputStream) o shouldBe empty
                e shouldBe empty
        })
    }

    it should s"run a swift script and confirm expected environment variables" in {
                val props = Seq(
                    "api_host" -> "xyz",
                    "api_key" -> "abc",
                    "namespace" -> "zzz",
                    "action_name" -> "xxx",
                    "activation_id" -> "iii",
                    "deadline" -> "123")
                val env = props.map { case (k, v) => s"__OW_${k.toUpperCase()}" -> v }

                val (out, err) = withActionContainer(env.take(1).toMap) { c =>
                    val (initCode, _) = c.init(initPayload(envCode))
                    initCode should be(200)

                    val (runCode, out) = c.run(runPayload(JsObject(), Some(props.toMap.toJson.asJsObject)))
                    runCode should be(200)
                    out shouldBe defined
                    props.map {
                        case (k, v) => withClue(k) {
                            out.get.fields(k) shouldBe JsString(v)
                        }

                    }
                }

                checkStreams(out, err, {
                    case (o, e) =>
                        if (enforceEmptyOutputStream) o shouldBe empty
                        if (enforceEmptyErrorStream) e shouldBe empty
                })
    }

}
