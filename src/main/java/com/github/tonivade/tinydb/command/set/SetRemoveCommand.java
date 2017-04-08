/*
 * Copyright (c) 2015-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */

package com.github.tonivade.tinydb.command.set;

import static com.github.tonivade.tinydb.data.DatabaseKey.safeKey;
import static com.github.tonivade.tinydb.data.DatabaseValue.set;
import static java.util.stream.Collectors.toList;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.github.tonivade.resp.annotation.Command;
import com.github.tonivade.resp.annotation.ParamLength;
import com.github.tonivade.resp.command.IRequest;
import com.github.tonivade.resp.protocol.RedisToken;
import com.github.tonivade.resp.protocol.SafeString;
import com.github.tonivade.tinydb.command.TinyDBCommand;
import com.github.tonivade.tinydb.command.annotation.ParamType;
import com.github.tonivade.tinydb.data.DataType;
import com.github.tonivade.tinydb.data.DatabaseValue;
import com.github.tonivade.tinydb.data.Database;

@Command("srem")
@ParamLength(2)
@ParamType(DataType.SET)
public class SetRemoveCommand implements TinyDBCommand {

  @Override
  public RedisToken<?> execute(Database db, IRequest request) {
    List<SafeString> items =  request.getParams().stream().skip(1).collect(toList());
    List<SafeString> removed = new LinkedList<>();
    db.merge(safeKey(request.getParam(0)), DatabaseValue.EMPTY_SET,
        (oldValue, newValue) -> {
          Set<SafeString> merge = new HashSet<>();
          merge.addAll(oldValue.getValue());
          for (SafeString item : items) {
            if (merge.remove(item)) {
              removed.add(item);
            }
          }
          return set(merge);
        });

    return RedisToken.integer(removed.size());
  }

}
