/*
 * Copyright (c) 2015-2017, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.claudb.data;

import static com.github.tonivade.resp.protocol.SafeString.safeString;
import static com.github.tonivade.claudb.data.DatabaseValue.score;
import static java.util.Collections.unmodifiableSet;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.nustaq.serialization.FSTConfiguration;

import com.github.tonivade.resp.protocol.SafeString;

public class SortedSetTest {

  private static final FSTConfiguration FST = FSTConfiguration.createDefaultConfiguration();
  
  static {
    FST.registerClass(SortedSet.class);
    FST.registerClass(SafeString.class);
  }

  @Test
  public void testSet() {
    SortedSet set = new SortedSet();

    assertThat(set.add(score(1, safeString("a"))), is(true));
    assertThat(set.add(score(2, safeString("a"))), is(false));
    assertThat(set.add(score(2, safeString("b"))), is(true));

    assertThat(set.contains(safeString("a")), is(true));
    assertThat(set.contains(safeString("b")), is(true));
    assertThat(set.contains(safeString("c")), is(false));

    assertThat(set.score(safeString("a")), is(1.0));
    assertThat(set.score(safeString("b")), is(2.0));

    assertThat(set.ranking(safeString("a")), is(0));
    assertThat(set.ranking(safeString("b")), is(1));

    assertThat(set.remove(safeString("a")), is(true));
    assertThat(set.contains(safeString("a")), is(false));
  }

  @Test
  public void testEquals() {
    SortedSet setA = new SortedSet();
    setA.add(score(1, safeString("a")));
    setA.add(score(2, safeString("b")));

    SortedSet setB = new SortedSet();
    setB.add(score(1, safeString("a")));
    setB.add(score(2, safeString("b")));

    assertThat(setA, is(setB));
    assertThat(unmodifiableSet(setA), is(unmodifiableSet(setB)));
  }

  @Test
  public void testNotEquals() {
    SortedSet setA = new SortedSet();
    setA.add(score(1, safeString("a")));

    SortedSet setB = new SortedSet();
    setB.add(score(1, safeString("a")));
    setB.add(score(2, safeString("b")));

    assertThat(setA, not(is(setB)));
  }

  @Test
  public void testScore() {
    SortedSet set = new SortedSet();
    set.add(score(1, safeString("a")));
    set.add(score(2, safeString("b")));
    set.add(score(3, safeString("c")));
    set.add(score(4, safeString("d")));
    set.add(score(5, safeString("e")));
    set.add(score(6, safeString("f")));
    set.add(score(7, safeString("g")));
    set.add(score(8, safeString("h")));
    set.add(score(9, safeString("i")));

    assertThat(set.tailSet(score(3, safeString(""))).first(), is(score(3.0, safeString("c"))));

    assertThat(set.headSet(score(4, safeString(""))).last(), is(score(3.0, safeString("c"))));
  }
  
  @Test
  public void testSerialization() {
    SortedSet set = new SortedSet();
    set.add(score(1, safeString("a")));
    set.add(score(2, safeString("b")));
    set.add(score(3, safeString("c")));
    set.add(score(4, safeString("d")));
    set.add(score(5, safeString("e")));
    set.add(score(6, safeString("f")));
    set.add(score(7, safeString("g")));
    set.add(score(8, safeString("h")));
    set.add(score(9, safeString("i")));
    
    SortedSet deserializedSet = (SortedSet) FST.asObject(FST.asByteArray(set));
    
    assertThat(deserializedSet, equalTo(set));
  }
}
