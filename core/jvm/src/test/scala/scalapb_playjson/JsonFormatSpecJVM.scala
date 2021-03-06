package scalapb_playjson

import org.scalatest.OptionValues
import jsontest.test._
import com.google.protobuf.util.{JsonFormat => JavaJsonFormat}
import com.google.protobuf.any.{Any => PBAny}
import com.google.protobuf.util.JsonFormat.{TypeRegistry => JavaTypeRegistry}
import scalapb_json._
import java.math.BigInteger
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class JsonFormatSpecJVM
  extends AnyFlatSpec
  with Matchers
  with OptionValues
  with JsonFormatSpecBase {
  val TestProto = MyTest().update(
    _.hello := "Foo",
    _.foobar := 37,
    _.primitiveSequence := Seq("a", "b", "c"),
    _.repMessage := Seq(MyTest(), MyTest(hello = Some("h11"))),
    _.optMessage := MyTest().update(_.foobar := 39),
    _.stringToInt32 := Map("foo" -> 14, "bar" -> 19),
    _.intToMytest := Map(14 -> MyTest(), 35 -> MyTest(hello = Some("boo"))),
    _.repEnum := Seq(MyEnum.V1, MyEnum.V2, MyEnum.UNKNOWN),
    _.optEnum := MyEnum.V2,
    _.intToEnum := Map(32 -> MyEnum.V1, 35 -> MyEnum.V2),
    _.stringToBool := Map("ff" -> false, "tt" -> true),
    _.boolToString := Map(false -> "ff", true -> "tt"),
    _.optBool := false
  )

  "fromJsonString" should "read json produced by Java" in {
    val javaJson = JavaJsonFormat.printer().print(MyTest.toJavaProto(TestProto))
    JsonFormat.fromJsonString[MyTest](javaJson) must be(TestProto)
  }

  "Java parser" should "read json strings produced by us" in {
    val b = jsontest.Test.MyTest.newBuilder
    JavaJsonFormat.parser().merge(JsonFormat.toJsonString(TestProto), b)
    TestProto must be(MyTest.fromJavaProto(b.build))
  }

  val anyEnabledJavaTypeRegistry =
    JavaTypeRegistry.newBuilder().add(TestProto.companion.javaDescriptor).build()
  val anyEnabledJavaPrinter = JavaJsonFormat.printer().usingTypeRegistry(anyEnabledJavaTypeRegistry)
  val anyEnabledTypeRegistry = TypeRegistry.empty.addMessageByCompanion(TestProto.companion)
  val anyEnabledParser = new Parser(typeRegistry = anyEnabledTypeRegistry)

  "Any" should "parse JSON produced by Java for a packed TestProto" in {
    val javaAny = com.google.protobuf.Any.pack(MyTest.toJavaProto(TestProto))
    val javaJson = anyEnabledJavaPrinter.print(javaAny)
    anyEnabledParser.fromJsonString[PBAny](javaJson).unpack[MyTest] must be(TestProto)
  }

  "booleans" should "be accepted as string" in {
    JsonFormat.fromJsonString[MyTest]("""{"optBool": "true"}""") must be(
      MyTest(optBool = Some(true))
    )
    JsonFormat.fromJsonString[MyTest]("""{"optBool": "false"}""") must be(
      MyTest(optBool = Some(false))
    )
  }

  "unknown fields" should "get rejected" in new DefaultParserContext {
    assertFails("""{"random_field_123": 3}""", MyTest)
    // There is special for @type field for anys, lets make sure they get rejected too
    assertFails("""{"@type": "foo"}""", MyTest)
  }

  "unknown fields" should "not get rejected when ignoreUnknownFields is set" in new IgnoringUnknownParserContext {
    assertParse("""{"random_field_123": 3}""", MyTest())
    // There is special for @type field for anys, lets make sure they get rejected too
    assertParse("""{"@type": "foo"}""", MyTest())
  }

  "parser" should "reject out of range numeric values" in {
    val maxLong = new BigInteger(String.valueOf(Long.MaxValue))
    val minLong = new BigInteger(String.valueOf(Long.MinValue))
    assertAcceptsNoQuotes("optionalInt64", maxLong.toString)
    assertAcceptsNoQuotes("optionalInt64", minLong.toString)

    val moreThanOne = new java.math.BigDecimal("1.000001")
    val maxDouble = new java.math.BigDecimal(Double.MaxValue)
    val minDouble = new java.math.BigDecimal(-Double.MaxValue)
    assertAccepts("optionalDouble", minDouble.toString)
    assertRejects("optionalDouble", maxDouble.multiply(moreThanOne).toString)
    assertRejects("optionalDouble", minDouble.multiply(moreThanOne).toString)
  }
}
