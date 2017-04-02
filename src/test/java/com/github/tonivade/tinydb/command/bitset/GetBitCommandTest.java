/*
 * Copyright (c) 2016-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */

package com.github.tonivade.tinydb.command.bitset;

import org.junit.Rule;
import org.junit.Test;

import com.github.tonivade.resp.protocol.RedisToken;
import com.github.tonivade.tinydb.command.CommandRule;
import com.github.tonivade.tinydb.command.CommandUnderTest;
import com.github.tonivade.tinydb.data.DatabaseValue;


@CommandUnderTest(GetBitCommand.class)
public class GetBitCommandTest {

  @Rule
  public final CommandRule rule = new CommandRule(this);

  @Test
  public void testExecuteOne()  {
    rule.withData("test", DatabaseValue.bitset(10))
    .withParams("test", "10")
    .execute()
    .then(RedisToken.integer(true));
  }

  @Test
  public void testExecuteZero()  {
    rule.withData("test", DatabaseValue.bitset())
    .withParams("test", "10")
    .execute()
    .then(RedisToken.integer(false));
  }

  @Test
  public void testExecuteFormat()  {
    rule.withData("test", DatabaseValue.bitset())
    .withParams("test", "a")
    .execute()
    .then(RedisToken.error("bit offset is not an integer"));
  }

}
