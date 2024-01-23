package cql

class ScannerTest extends BaseTest {
  def eofToken(start: Int) = Token(TokenType.EOF, "", None, start, start)
  def unquotedStringToken(str: String, start: Int = 0) = Token(TokenType.STRING, str, Some(str), start, start + str.length - 1)
  def quotedStringToken(str: String, start: Int = 0) = Token(TokenType.STRING, s"\"$str\"", Some(str), start, start + str.length + 1)

  describe("unquoted strings") {
    it("should parse plain strings") {
      val scanner = new Scanner("""sausages""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        unquotedStringToken("sausages"),
        eofToken(8)
      )
      assert(tokens === expectedTokens)
    }

    it("should give multiple tokens for strings separated with a space") {
      val scanner = new Scanner("""magnificent octopus""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        unquotedStringToken("magnificent"),
        unquotedStringToken("octopus", 12),
        eofToken(19)
      )
      assert(tokens === expectedTokens)
    }
  }

  describe("quoted strings") {
    it("should parse plain strings") {
      val scanner = new Scanner(""""sausages"""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        quotedStringToken("sausages"),
        eofToken(10)
      )
      assert(tokens === expectedTokens)
    }

    it("should give a single token for strings separated with a space") {
      val scanner = new Scanner(""""magnificent octopus"""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        quotedStringToken("magnificent octopus"),
        eofToken(21)
      )
      assert(tokens === expectedTokens)
    }
  }

  describe("search params") {
    it("should tokenise tags") {
      val scanner = new Scanner("""+tag:tone/news""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        Token(TokenType.QUERY_META_KEY, "+tag", Some("tag"), 0, 3),
        Token(TokenType.QUERY_META_VALUE, ":tone/news",  Some("tone/news"), 4, 13),
        eofToken(14)
      )
      assert(tokens === expectedTokens)
    }

    it("should tokenise sections") {
      val scanner = new Scanner("""+section:commentisfree""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        Token(TokenType.QUERY_META_KEY, "+section", Some("section"), 0, 7),
        Token(TokenType.QUERY_META_VALUE, ":commentisfree", Some("commentisfree"), 8, 21),
        eofToken(22)
      )
      assert(tokens === expectedTokens)
    }

    it("should yield a plus token when a search key is incomplete") {
      val scanner = new Scanner("""example +""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        unquotedStringToken("example"),
        Token(TokenType.PLUS, "+", None, 8, 8),
        eofToken(9)
      )
      assert(tokens === expectedTokens)
    }

    it("should yield a colon token when a query meta value is incomplete") {
      val scanner = new Scanner("""example +tag:""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        unquotedStringToken("example"),
        Token(TokenType.QUERY_META_KEY, "+tag", Some("tag"), 8, 11),
        Token(TokenType.COLON, ":", None, 12, 12),
        eofToken(13)
      )
      assert(tokens === expectedTokens)
    }

    it("should tokenise groups and boolean operators") {
      val scanner = new Scanner("""one AND (two OR three)""")
      val tokens = scanner.scanTokens
      val expectedTokens = List(
        unquotedStringToken("one"),
        Token(TokenType.AND, "AND", None, 4, 6),
        Token(TokenType.LEFT_BRACKET, "(", None, 8, 8),
        unquotedStringToken("two", 9),
        Token(TokenType.OR, "OR", None, 13, 14),
        unquotedStringToken("three", 16),
        Token(TokenType.RIGHT_BRACKET, ")", None, 21, 21),
        eofToken(22)
      )
      assert(tokens === expectedTokens)
    }
  }
}
