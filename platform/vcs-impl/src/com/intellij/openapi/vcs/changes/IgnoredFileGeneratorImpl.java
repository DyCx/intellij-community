// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.notification.NotificationAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class IgnoredFileGeneratorImpl implements IgnoredFileGenerator {

  private static final Logger LOG = Logger.getInstance(IgnoredFileGeneratorImpl.class);

  private final Project myProject;

  private final Object myWriteLock = new Object();

  protected IgnoredFileGeneratorImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean generateFile(@NotNull VirtualFile ignoreFileRoot, @NotNull AbstractVcs vcs) throws IOException {
    IgnoredFileContentProvider ignoredFileContentProvider = findIgnoredFileContentProvider(vcs);
    if (ignoredFileContentProvider == null) {
      LOG.debug("Cannot find content provider for vcs " + vcs.getName());
      return false;
    }

    String ignoreFileName = ignoredFileContentProvider.getFileName();

    if (!needGenerateIgnoreFile(myProject, ignoreFileRoot)) {
      LOG.debug("Skip VCS ignore file generation");
      return false;
    }

    synchronized (myWriteLock) {
      File ignoreFile = getIgnoreFile(ignoreFileRoot, ignoreFileName);
      if (!ignoreFile.exists()) {
        String projectCharsetName = EncodingProjectManager.getInstance(myProject).getDefaultCharsetName();
        String ignoreFileContent = ignoredFileContentProvider.buildIgnoreFileContent(ignoreFileRoot, IgnoredFileProvider.IGNORE_FILE.getExtensions());
        FileUtil.writeToFile(ignoreFile, ignoreFileContent.getBytes(projectCharsetName));
        LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(ignoreFile));
        notifyAboutIgnoreFileGeneration(ignoreFile);
        return true;
      }
      return false;
    }
  }

  @Nullable
  private IgnoredFileContentProvider findIgnoredFileContentProvider(@NotNull AbstractVcs vcs) {
    IgnoredFileContentProvider[] contentProviders = IgnoredFileContentProvider.IGNORE_FILE_CONTENT_PROVIDER.getExtensions(myProject);
    return Arrays.stream(contentProviders)
      .filter((provider) -> provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod()))
      .findFirst().orElse(null);
  }

  @NotNull
  private static File getIgnoreFile(@NotNull VirtualFile ignoreFileRoot, @NotNull String ignoreFileName) {
    File vcsRootFile = VfsUtilCore.virtualToIoFile(ignoreFileRoot);
    return new File(vcsRootFile.getPath(), ignoreFileName);
  }

  private void notifyAboutIgnoreFileGeneration(@NotNull File ignoreFile) {
    IgnoredFileRootStore.getInstance(myProject).addRoot(ignoreFile.getParent());
    VcsNotifier.getInstance(myProject)
      .notifyMinorInfo("",
                       VcsBundle.message("ignored.file.generation.message", ignoreFile.getName()),
                       NotificationAction.create(VcsBundle.message("ignored.file.generation.review"), (event, notification) -> {
                         notification.expire();
                         VirtualFile ignoreVirtualFile = VfsUtil.findFileByIoFile(ignoreFile, true);
                         if (ignoreVirtualFile != null) {
                           new OpenFileDescriptor(myProject, ignoreVirtualFile).navigate(true);
                         }
                         else {
                           LOG.warn("Cannot find ignore file " + ignoreFile.getName());
                         }
                       }));
  }

  private static boolean needGenerateIgnoreFile(@NotNull Project project, @NotNull VirtualFile ignoreFileRoot) {
    boolean wasGeneratedPreviously = IgnoredFileRootStore.getInstance(project).containsRoot(ignoreFileRoot.getPath());
    if (wasGeneratedPreviously) {
      LOG.debug("Ignore file generated previously for root " + ignoreFileRoot.getPath());
    }
    boolean needGenerateRegistryFlag = Registry.is("vcs.ignorefile.generation", true);
    return !wasGeneratedPreviously && needGenerateRegistryFlag;
  }

  @State(name = "IgnoredFileRootStore", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
  static class IgnoredFileRootStore implements PersistentStateComponent<IgnoredFileRootStore.State> {

    static class State {
      public Set<String> generatedRoots = ContainerUtil.newHashSet();
    }

    State myState;

    static IgnoredFileRootStore getInstance(Project project) {
      return ServiceManager.getService(project, IgnoredFileRootStore.class);
    }

    boolean containsRoot(@NotNull String root) {
      return myState != null && myState.generatedRoots.contains(root);
    }

    void addRoot(@NotNull String root) {
      if (myState != null) {
        myState.generatedRoots.add(root);
      }
    }

    @Nullable
    @Override
    public State getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
      myState = state;
    }
  }
}
