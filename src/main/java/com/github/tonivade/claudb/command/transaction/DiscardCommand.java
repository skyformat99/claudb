/*
 * Copyright (c) 2015-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.claudb.command.transaction;

import java.util.Optional;

import com.github.tonivade.resp.annotation.Command;
import com.github.tonivade.resp.command.Request;
import com.github.tonivade.resp.command.Session;
import com.github.tonivade.resp.protocol.RedisToken;
import com.github.tonivade.claudb.TransactionState;
import com.github.tonivade.claudb.command.DBCommand;
import com.github.tonivade.claudb.command.annotation.TxIgnore;
import com.github.tonivade.claudb.data.Database;

@Command("discard")
@TxIgnore
public class DiscardCommand implements DBCommand {

  private static final String TX_KEY = "tx";

  @Override
  public RedisToken execute(Database db, Request request) {
    removeTransactionIfExists(request.getSession());

    return RedisToken.responseOk();
  }

  private Optional<TransactionState> removeTransactionIfExists(Session session) {
    return session.removeValue(TX_KEY);
  }
}
