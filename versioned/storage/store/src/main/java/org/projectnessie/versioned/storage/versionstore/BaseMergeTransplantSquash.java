/*
 * Copyright (C) 2022 Dremio
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
package org.projectnessie.versioned.storage.versionstore;

import static java.util.Collections.emptyList;
import static org.projectnessie.versioned.storage.common.logic.CreateCommit.newCommitBuilder;
import static org.projectnessie.versioned.storage.common.logic.DiffQuery.diffQuery;
import static org.projectnessie.versioned.storage.common.logic.Logics.commitLogic;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.fromCommitMeta;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.toCommitMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentKey;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Commit;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ImmutableMergeResult;
import org.projectnessie.versioned.MergeResult;
import org.projectnessie.versioned.MergeResult.KeyDetails;
import org.projectnessie.versioned.MergeType;
import org.projectnessie.versioned.MetadataRewriter;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.storage.common.indexes.StoreKey;
import org.projectnessie.versioned.storage.common.logic.CommitLogic;
import org.projectnessie.versioned.storage.common.logic.CommitRetry.RetryException;
import org.projectnessie.versioned.storage.common.logic.CreateCommit;
import org.projectnessie.versioned.storage.common.logic.DiffEntry;
import org.projectnessie.versioned.storage.common.logic.PagedResult;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;

class BaseMergeTransplantSquash extends BaseCommitHelper {

  BaseMergeTransplantSquash(
      @Nonnull @jakarta.annotation.Nonnull BranchName branch,
      @Nonnull @jakarta.annotation.Nonnull Optional<Hash> referenceHash,
      @Nonnull @jakarta.annotation.Nonnull Persist persist,
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nullable @jakarta.annotation.Nullable CommitObj head)
      throws ReferenceNotFoundException {
    super(branch, referenceHash, persist, reference, head);
  }

  MergeResult<Commit> squash(
      boolean dryRun,
      ImmutableMergeResult.Builder<Commit> mergeResult,
      Function<ContentKey, MergeType> mergeTypeForKey,
      MetadataRewriter<CommitMeta> updateCommitMetadata,
      SourceCommitsAndParent sourceCommits,
      @Nullable @jakarta.annotation.Nullable ObjId mergeFromId)
      throws RetryException, ReferenceNotFoundException {

    CreateCommit createCommit =
        createSquashCommit(updateCommitMetadata, sourceCommits, mergeFromId);

    Map<ContentKey, KeyDetails> keyDetailsMap = new HashMap<>();
    CommitObj mergeCommit =
        createMergeTransplantCommit(mergeTypeForKey, keyDetailsMap, createCommit);

    CommitLogic commitLogic = commitLogic(persist);
    boolean committed = commitLogic.storeCommit(mergeCommit, emptyList());

    ObjId newHead;
    if (committed) {
      newHead = mergeCommit.id();
    } else {
      // Commit has NOT been persisted, because it already exists.
      //
      // This MAY indicate a fast-forward merge.
      // But it may also indicate that another request created the exact same commit, BUT that
      // other commit does not necessarily need to be included in the current reference chain.
      //
      // TL;DR assuming that 'new_head == null' indicates a fast-forward is WRONG.
      newHead = mergeCommit.id();
    }

    return mergeTransplantSuccess(mergeResult, newHead, dryRun, keyDetailsMap);
  }

  private CreateCommit createSquashCommit(
      MetadataRewriter<CommitMeta> updateCommitMetadata,
      SourceCommitsAndParent sourceCommits,
      @Nullable @jakarta.annotation.Nullable ObjId mergeFromId) {
    CreateCommit.Builder commitBuilder = newCommitBuilder().parentCommitId(headId());

    List<CommitMeta> commitsMetadata = new ArrayList<>(sourceCommits.sourceCommits.size());
    for (CommitObj sourceCommit : sourceCommits.sourceCommits) {
      commitsMetadata.add(toCommitMeta(sourceCommit));
    }

    CommitMeta metadata = updateCommitMetadata.squash(commitsMetadata);
    fromCommitMeta(metadata, commitBuilder);

    if (mergeFromId != null) {
      commitBuilder.addSecondaryParents(mergeFromId);
    }

    CommitLogic commitLogic = commitLogic(persist);
    PagedResult<DiffEntry, StoreKey> diff =
        commitLogic.diff(diffQuery(sourceCommits.sourceParent, sourceCommits.mostRecent(), true));
    return commitLogic.diffToCreateCommit(diff, commitBuilder).build();
  }
}