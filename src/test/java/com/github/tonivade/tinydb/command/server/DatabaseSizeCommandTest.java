package com.github.tonivade.tinydb.command.server;

import static com.github.tonivade.resp.protocol.RedisToken.integer;
import static com.github.tonivade.tinydb.data.DatabaseValue.string;

import org.junit.Rule;
import org.junit.Test;

import com.github.tonivade.tinydb.command.CommandRule;
import com.github.tonivade.tinydb.command.CommandUnderTest;

@CommandUnderTest(DatabaseSizeCommand.class)
public class DatabaseSizeCommandTest {

  @Rule
  public final CommandRule rule = new CommandRule(this);

  @Test
  public void testExecuteEmpty() {
    rule.execute()
        .assertThat(integer(0));
  }

  @Test
  public void testExecute() {
    rule.withData("key", string("value"))
        .execute()
        .assertThat(integer(1));
  }
}
