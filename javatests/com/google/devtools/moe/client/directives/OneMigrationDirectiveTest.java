/*
 * Copyright (c) 2011 Google, Inc.
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

package com.google.devtools.moe.client.directives;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.Injector;
import com.google.devtools.moe.client.MoeProblem;
import com.google.devtools.moe.client.SystemCommandRunner;
import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.migrations.Migrator;
import com.google.devtools.moe.client.project.ProjectContext;
import com.google.devtools.moe.client.repositories.MetadataScrubber;
import com.google.devtools.moe.client.repositories.Repositories;
import com.google.devtools.moe.client.repositories.RepositoryType;
import com.google.devtools.moe.client.testing.DummyRepositoryFactory;
import com.google.devtools.moe.client.testing.InMemoryProjectContextFactory;
import com.google.devtools.moe.client.writer.DraftRevision;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;

public class OneMigrationDirectiveTest extends TestCase {
  private static final ImmutableSet<MetadataScrubber> NO_SCRUBBERS = ImmutableSet.of();
  private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
  private final Ui ui = new Ui(stream, /* fileSystem */ null);
  private final SystemCommandRunner cmd = new SystemCommandRunner();
  private ProjectContext context;

  @Override
  public void setUp() throws Exception {
    Injector.INSTANCE = new Injector(null, cmd, ui);
    super.setUp();
    Repositories repositories =
        new Repositories(ImmutableSet.<RepositoryType.Factory>of(new DummyRepositoryFactory(null)));
    InMemoryProjectContextFactory contextFactory =
        new InMemoryProjectContextFactory(null, cmd, null, ui, repositories);
    contextFactory.projectConfigs.put(
        "moe_config.txt",
        "{\"name\":\"foo\",\"repositories\":{"
            + "\"int\":{\"type\":\"dummy\",\"project_space\":\"internal\"},"
            + "\"pub\":{\"type\":\"dummy\"}},"
            + "\"translators\":[{\"from_project_space\":\"internal\","
            + "\"to_project_space\":\"public\",\"steps\":[{\"name\":\"id_step\","
            + "\"editor\":{\"type\":\"identity\"}}]}]}");
    context = contextFactory.create("moe_config.txt");
  }

  public void testOneMigration() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            context.config(),
            context,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), NO_SCRUBBERS, ui));
    d.fromRepository = "int(revision=1000)";
    d.toRepository = "pub(revision=2)";
    assertThat(d.perform()).isEqualTo(0);
    assertThat(stream.toString()).contains("Created Draft Revision: /dummy/revision/pub");
  }

  public void testOneMigrationFailOnFromRevision() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            context.config(),
            context,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), NO_SCRUBBERS, ui));
    d.fromRepository = "x(revision=1000)";
    d.toRepository = "pub(revision=2)";
    try {
      d.perform();
      fail("OneMigrationDirective didn't fail on invalid repository 'x'.");
    } catch (MoeProblem expected) {
      assertEquals(
          "No such repository 'x' in the config. Found: [int, pub]", expected.getMessage());
    }
  }

  public void testOneMigrationFailOnToRevision() throws Exception {
    OneMigrationDirective d =
        new OneMigrationDirective(
            context.config(),
            context,
            ui,
            new DraftRevision.Factory(ui),
            new Migrator(new DraftRevision.Factory(ui), NO_SCRUBBERS, ui));
    d.fromRepository = "int(revision=1000)";
    d.toRepository = "x(revision=2)";
    try {
      d.perform();
      fail("OneMigrationDirective didn't fail on invalid repository 'x'.");
    } catch (MoeProblem expected) {
      assertEquals(
          "No such repository 'x' in the config. Found: [int, pub]", expected.getMessage());
    }
  }
}
