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


@RunWith(classOf[JUnitRunner])
class Swift4ActionContainerTests extends Swift3ActionContainerTests {
    override lazy val swiftContainerImageName = "action-swift-v4.0.0"
    override lazy val helloSwiftZipName = "helloSwift400.zip"
    override lazy val watsonCode = """
                | import AlchemyDataNewsV1
                | import ConversationV1
                | import DiscoveryV1
                | import DocumentConversionV1
                | import NaturalLanguageClassifierV1
                | import NaturalLanguageUnderstandingV1
                | import PersonalityInsightsV3
                | import RetrieveAndRankV1
                | import ToneAnalyzerV3
                | import TradeoffAnalyticsV1
                | import VisualRecognitionV3
                |
                | func main(args: [String:Any]) -> [String:Any] {
                |     return ["message": "I compiled and was able to import Watson SDKs"]
                | }
            """.stripMargin
}
