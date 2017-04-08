/*
 * Copyright (c) 2015-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */

package com.github.tonivade.tinydb.command.server;

import static java.lang.Integer.parseInt;

import com.github.tonivade.resp.annotation.Command;
import com.github.tonivade.resp.annotation.ParamLength;
import com.github.tonivade.resp.command.IRequest;
import com.github.tonivade.resp.protocol.RedisToken;
import com.github.tonivade.tinydb.command.TinyDBCommand;
import com.github.tonivade.tinydb.command.annotation.ReadOnly;
import com.github.tonivade.tinydb.data.Database;

@ReadOnly
@Command("select")
@ParamLength(1)
public class SelectCommand implements TinyDBCommand {

  @Override
  public RedisToken<?> execute(Database db, IRequest request) {
    try {
      getSessionState(request.getSession()).setCurrentDB(parseCurrentDB(request));
      return RedisToken.responseOk();
    } catch (NumberFormatException e) {
      return RedisToken.error("ERR invalid DB index");
    }

  }

  private int parseCurrentDB(IRequest request) {
    return parseInt(request.getParam(0).toString());
  }

}
