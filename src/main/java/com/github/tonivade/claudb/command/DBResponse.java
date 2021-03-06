/*
 * Copyright (c) 2015-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.claudb.command;

import static com.github.tonivade.resp.protocol.RedisToken.array;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;
import static io.vavr.Predicates.is;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.function.Function;

import com.github.tonivade.resp.protocol.RedisToken;
import com.github.tonivade.resp.protocol.SafeString;
import com.github.tonivade.claudb.data.DatabaseValue;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.Stream;

class DBResponse {

  static RedisToken convertValue(DatabaseValue value) {
    if (value != null) {
      switch (value.getType()) {
      case STRING:
          SafeString string = value.getString();
          return RedisToken.string(string);
      case HASH:
          Map<SafeString, SafeString> map = value.getHash();
          return array(keyValueList(map).toJavaList());
      case LIST:
          List<SafeString> list = value.getList();
          return convertArray(list.toJavaList());
      case SET:
          Set<SafeString> set = value.getSet();
          return convertArray(set.toJavaList());
      case ZSET:
          NavigableSet<Entry<Double, SafeString>> zset = value.getSortedSet();
          return convertArray(serialize(zset));
      default:
        break;
      }
    }
    return RedisToken.nullString();
  }

  static RedisToken convertArray(Collection<?> array) {
    if (array == null) {
      return RedisToken.array();
    }
    return RedisToken.array(array.stream().map(DBResponse::parseToken).collect(toList()));
  }

  private static RedisToken parseToken(Object value) {
    return Match(value).of(
        Case($(instanceOf(Integer.class)), RedisToken::integer),
        Case($(instanceOf(Boolean.class)), RedisToken::integer),
        Case($(instanceOf(String.class)), RedisToken::string),
        Case($(instanceOf(Double.class)), x -> RedisToken.string(x.toString())),
        Case($(instanceOf(SafeString.class)), RedisToken::string),
        Case($(instanceOf(DatabaseValue.class)), DBResponse::convertValue),
        Case($(instanceOf(RedisToken.class)), Function.identity()),
        Case($(is(null)), ignore -> RedisToken.nullString()));
  }

  private static List<RedisToken> keyValueList(Map<SafeString, SafeString> map) {
    return map
        .flatMap(entry -> Stream.of(entry._1(), entry._2()))
        .map(RedisToken::string)
        .collect(List.collector());
  }

  private static Collection<?> serialize(NavigableSet<Entry<Double, SafeString>> set) {
    return set.stream()
        .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()).toJavaStream()).collect(toList());
  }
}
