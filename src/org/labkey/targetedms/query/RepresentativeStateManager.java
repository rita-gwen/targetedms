/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.targetedms.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.security.User;
import org.labkey.targetedms.TargetedMSManager;
import org.labkey.targetedms.TargetedMSRun;
import org.labkey.targetedms.TargetedMsRepresentativeStateAuditViewFactory;
import org.labkey.targetedms.chromlib.ChromatogramLibraryUtils;
import org.labkey.targetedms.parser.PeptideGroup;
import org.labkey.targetedms.parser.Precursor;
import org.labkey.targetedms.parser.RepresentativeDataState;

import java.util.Collections;
import java.util.List;

/**
 * User: vsharma
 * Date: 11/19/12
 * Time: 9:54 PM
 */
public class RepresentativeStateManager
{
    private RepresentativeStateManager() {}

    public static void setRepresentativeState(User user, Container container,
                                             TargetedMSRun run, TargetedMSRun.RepresentativeDataState state)
    {
        try (DbScope.Transaction transaction = TargetedMSManager.getSchema().getScope().ensureTransaction())
        {
            int conflictCount = 0;
            if(state == TargetedMSRun.RepresentativeDataState.Representative_Protein)
            {
                conflictCount = resolveRepresentativeProteinState(container, run);
            }
            else if(state == TargetedMSRun.RepresentativeDataState.Representative_Peptide)
            {
                conflictCount = resolveRepresentativePeptideState(container, run);
            }
            else if(state == TargetedMSRun.RepresentativeDataState.NotRepresentative)
            {
                TargetedMSRun.RepresentativeDataState currentState = run.getRepresentativeDataState();
                if(currentState == TargetedMSRun.RepresentativeDataState.Representative_Protein)
                {
                    revertProteinRepresentativeState(user, container, run);
                }
                else if(currentState == TargetedMSRun.RepresentativeDataState.Representative_Peptide)
                {
                    revertPeptideRepresentativeState(user, container, run);
                }
            }
            else
                throw new IllegalArgumentException("Unrecognized representative data state: "+state);

            run.setRepresentativeDataState(state);

            // If there are runs in the container that no longer have any representative data mark
            // them as being not representative.
            TargetedMSManager.markRunsNotRepresentative(container, TargetedMSRun.RepresentativeDataState.Representative_Protein);

            // Increment the chromatogram library revision number for this container.
            ChromatogramLibraryUtils.incrementLibraryRevision(container);

            // Add event to audit log.
            TargetedMsRepresentativeStateAuditViewFactory.addAuditEntry(container, user,
                    "Updated representative state. Number of conflicts " + conflictCount);

            transaction.commit();
        }
        Table.update(user, TargetedMSManager.getTableInfoRuns(), run, run.getId());
    }

    private static void revertProteinRepresentativeState(User user, Container container, TargetedMSRun run)
    {
        // Get a list of proteins in this run that are marked as representative.
        List<PeptideGroup> representativeGroups = PeptideGroupManager.getRepresentativePeptideGroups(run.getRunId());

        // Roll back representative state to the most recently deprecated proteins in other runs
        // in this container(folder).
        for(PeptideGroup pepGrp: representativeGroups)
        {
            PeptideGroup lastDeprecatedGroup = PeptideGroupManager.getLastDeprecatedPeptideGroup(pepGrp, container);
            if(lastDeprecatedGroup != null)
            {
                // Mark the last deprecated protein as representative
                lastDeprecatedGroup.setRepresentativeDataState(RepresentativeDataState.Representative);
                Table.update(user, TargetedMSManager.getTableInfoPeptideGroup(), lastDeprecatedGroup, lastDeprecatedGroup.getId());

                // Set the representative state of all the precursors in this peptide group to be the same
                // as the representative state of the peptide group
                updatePrecursorRepresentativeState(lastDeprecatedGroup);
            }
        }

        // Mark all proteins in this run as not representative
        PeptideGroupManager.setRepresentativeState(run.getId(), RepresentativeDataState.NotRepresentative);

        // Set the representative state of all the precursors in this run to not-representative
        updatePrecursorRepresentativeState(run);
    }

    private static void revertPeptideRepresentativeState(User user, Container container, TargetedMSRun run)
    {
        // Get a list of precursors in this run that are marked as representative.
        List<Precursor> representativePrecursors = PrecursorManager.getRepresentativePrecursors(run.getRunId());

        // Roll back representative state to the most recently deprecated precursors in other runs
        // in this container(folder).
        for(Precursor prec: representativePrecursors)
        {
            Precursor lastDeprecatedPrec = PrecursorManager.getLastDeprecatedPrecursor(prec, container);
            if(lastDeprecatedPrec != null)
            {
                // Mark the last deprecated precursor as representative
                lastDeprecatedPrec.setRepresentativeDataState(RepresentativeDataState.Representative);
                Table.update(user, TargetedMSManager.getTableInfoPrecursor(), lastDeprecatedPrec, lastDeprecatedPrec.getId());
            }
        }

        // Mark all precursors in this run as not representative
        PrecursorManager.setRepresentativeState(run.getId(), RepresentativeDataState.NotRepresentative);
    }

    private static int resolveRepresentativeProteinState(Container container, TargetedMSRun run)
    {
        // Get a list of peptide group ids that only have either decoy or standard peptides
        List<Integer> peptideGroupIdsToExclude = getAllDecoyOrStandardPeptideGroups(run);

        // Mark everything in this run that doesn't already have representative data in this container as being active
        SQLFragment makeActiveSQL = new SQLFragment("UPDATE " + TargetedMSManager.getTableInfoPeptideGroup());
        makeActiveSQL.append(" SET RepresentativeDataState = ?");
        makeActiveSQL.add(RepresentativeDataState.Representative.ordinal());
        makeActiveSQL.append(" WHERE RunId=? ");
        makeActiveSQL.add(run.getId());
        if(peptideGroupIdsToExclude.size() > 0)
        {
            makeActiveSQL.append(" AND Id NOT IN (" + StringUtils.join(peptideGroupIdsToExclude, ',') + ")");
        }
        makeActiveSQL.append(" AND( ");
        // If this peptide group has a SequenceId make sure we don't have another peptide group in this container
        // with the same SequenceId that has been previously marked as representative
        makeActiveSQL.append(" (SequenceId IS NOT NULL AND (SequenceId NOT IN (SELECT SequenceId FROM ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg1");
        makeActiveSQL.append(", ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoRuns(), "r1");
        makeActiveSQL.append(" WHERE pg1.RunId = r1.Id AND r1.Container=? AND pg1.RepresentativeDataState=?))) ");
        makeActiveSQL.add(container);
        makeActiveSQL.add(RepresentativeDataState.Representative.ordinal());
        // If the peptide group does not have a SequenceId or there isn't an older peptide group with the same
        // SequenceId, compare the Labels to look for conflicting proteins.
        makeActiveSQL.append(" OR (Label NOT IN (SELECT Label FROM ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg2");
        makeActiveSQL.append(", ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoRuns(), "r2");
        makeActiveSQL.append(" WHERE pg2.RunID = r2.Id AND r2.Container=? AND pg2.RepresentativeDataState=?)) ");
        makeActiveSQL.add(container);
        makeActiveSQL.add(RepresentativeDataState.Representative.ordinal());
        makeActiveSQL.append(")");
        new SqlExecutor(TargetedMSManager.getSchema()).execute(makeActiveSQL);

        // Mark all other peptide groups as being in the conflicted state
        SQLFragment makeConflictedSQL = new SQLFragment("UPDATE "+TargetedMSManager.getTableInfoPeptideGroup());
        makeConflictedSQL.append(" SET RepresentativeDataState = ?");
        makeConflictedSQL.add(RepresentativeDataState.Conflicted.ordinal());
        makeConflictedSQL.append(" WHERE RunId=?");
        makeConflictedSQL.add(run.getId());
        if(peptideGroupIdsToExclude.size() > 0)
        {
            makeConflictedSQL.append(" AND Id NOT IN (" + StringUtils.join(peptideGroupIdsToExclude, ',') + ")");
        }
        makeConflictedSQL.append(" AND RepresentativeDataState != ?");
        makeConflictedSQL.add(RepresentativeDataState.Representative.ordinal());
        int conflictCount = new SqlExecutor(TargetedMSManager.getSchema()).execute(makeConflictedSQL);

        // Issue 23843: Include iRT peptides in chromatogram libraries
        // We do not want users to have to resolve conflicts for standard peptides/proteins.  The latest version of the
        // "standard" protein (containing only standard peptides) will be marked as representative.
        // If this run has "standard" proteins, mark them as being representative.
        // Older versions of the proteins, from previous runs, will be marked as deprecated.
        List<Integer> standardPeptideGroupIds = getAllStandardPeptideGroups(run);
        if(standardPeptideGroupIds != null && standardPeptideGroupIds.size() > 0)
        {
            updateStandardPeptideGroups(container, standardPeptideGroupIds);
        }

        // Set the representative state of all the precursors in this run to be the same
        // as the representative state of the proteins
        updatePrecursorRepresentativeState(run);


        return conflictCount;
    }

    private static void updateStandardPeptideGroups(Container container, List<Integer> stdPepGrpIdsInRun)
    {
        // Get a list of the current standard peptide group ids in the folder that have the same label
        // as the standard peptide groups in the new run.
        List<Integer> currentStdPepGrpIds = getCurrentStandardPeptideGroupIds(container, stdPepGrpIdsInRun);

        // Set RepresentativeDataState of the new ones to Representative.
        PeptideGroupManager.updateRepresentativeStatus(stdPepGrpIdsInRun, RepresentativeDataState.Representative);

        // Set old ones to Representative_Deprecated.
        PeptideGroupManager.updateStatusToDeprecatedOrNotRepresentative(currentStdPepGrpIds);
    }

    private static List<Integer> getCurrentStandardPeptideGroupIds(Container container, List<Integer> stdPepGrpIdsInRun)
    {
        SQLFragment sql = new SQLFragment("SELECT pg.Id FROM ");
        sql.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoRuns(), "run");
        sql.append(" ON (run.Id = pg.RunId AND run.Container=?) ");
        sql.add(container);
        sql.append(" WHERE pg.Label IN (");
        sql.append(" SELECT Label FROM ").append(TargetedMSManager.getTableInfoPeptideGroup(), "pepgrp");
        sql.append(" WHERE Id IN (");
        sql.append(StringUtils.join(stdPepGrpIdsInRun, ","));
        sql.append(")");
        sql.append(")");
        sql.append(" AND pg.RepresentativeDataState=").append(RepresentativeDataState.Representative.ordinal());

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Integer.class);
    }

    private static List<Integer> getAllDecoyOrStandardPeptideGroups(TargetedMSRun run)
    {
        // Get a list of peptide group ids that only have either decoy or standard peptides
        return getFilteredPeptideGroupIds(run, true, true);
    }

    private static List<Integer> getAllStandardPeptideGroups(TargetedMSRun run)
    {
        // Get a list of peptide group ids that only have either decoy or standard peptides
        return getFilteredPeptideGroupIds(run, true, false);
    }

    private static List<Integer> getFilteredPeptideGroupIds(TargetedMSRun run, boolean includeStandard, boolean includeDecoy)
    {
        // Get a list of peptide group ids that only have either decoy or standard peptides
        SQLFragment sql = new SQLFragment("SELECT DISTINCT Id FROM " + TargetedMSManager.getTableInfoPeptideGroup());
        sql.append(" WHERE RunId = ? ");
        sql.add(run.getId());
        sql.append(" AND Id NOT IN (");
        sql.append(" SELECT pg.Id FROM ");
        sql.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg");
        sql.append(", ");
        sql.append(TargetedMSManager.getTableInfoPeptide(), "pep");
        sql.append(" WHERE ");
        sql.append("pep.PeptideGroupId = pg.Id");
        sql.append(" AND pg.RunId = ?");
        sql.add(run.getId());
        if(includeDecoy)
        {
            sql.append(" AND (pep.Decoy IS NULL OR pep.Decoy = " +
                    TargetedMSManager.getSqlDialect().getBooleanFALSE() + ")");
        }
        if(includeStandard)
        {
            sql.append(" AND pep.StandardType IS NULL ");
        }
        sql.append(")");

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Integer.class);
    }

    private static List<Integer> getStdPrecursorIdsInRun(TargetedMSRun run)
    {
        // Get a list of peptide group ids that only have either decoy or standard peptides
        SQLFragment sql = new SQLFragment("SELECT pre.Id FROM ");
        sql.append(TargetedMSManager.getTableInfoPrecursor(), "pre");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoPeptide(), "pep");
        sql.append(" ON (pep.Id = pre.PeptideId AND pep.StandardType IS NOT NULL) ");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg");
        sql.append(" ON (pg.Id = pep.PeptideGroupId AND pg.RunId = ?) ");
        sql.add(run.getId());

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Integer.class);
    }

    private static List<Integer> getCurrentStandardPrecursorIds(Container container, List<Integer> stdPrecursorIdsInRun)
    {
        if(stdPrecursorIdsInRun == null || stdPrecursorIdsInRun.size() == 0)
        {
            return Collections.emptyList();
        }
        SQLFragment sql = new SQLFragment("SELECT pre2.Id FROM ");
        sql.append(TargetedMSManager.getTableInfoPrecursor(), "pre1");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoPrecursor(), "pre2");
        sql.append(" ON ((");
        sql.append(TargetedMSManager.getSqlDialect().concatenate("pre1.ModifiedSequence", "CAST(pre1.Charge AS varchar)"));
        sql.append(") = (");
        sql.append(TargetedMSManager.getSqlDialect().concatenate("pre2.ModifiedSequence", "CAST(pre2.Charge AS varchar)"));
        sql.append(")");
        sql.append(" AND pre2.RepresentativeDataState=?");
        sql.add(RepresentativeDataState.Representative.ordinal());
        sql.append(")");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoPeptide(), "pep");
        sql.append(" ON (pep.Id = pre2.PeptideId) ");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg");
        sql.append(" ON (pg.Id = pep.PeptideGroupId) ");
        sql.append(" INNER JOIN ");
        sql.append(TargetedMSManager.getTableInfoRuns(), "run");
        sql.append(" ON (run.Id = pg.RunId AND run.Container=?) ");
        sql.add(container);
        sql.append(" WHERE pre1.Id in (").append(StringUtils.join(stdPrecursorIdsInRun, ",")).append(")");

        return new SqlSelector(TargetedMSManager.getSchema(), sql).getArrayList(Integer.class);
    }

    private static void updatePrecursorRepresentativeState(TargetedMSRun run)
    {
       updatePrecursorRepresentativeState("pg.RunId", run.getId());
    }

    private static void updatePrecursorRepresentativeState(PeptideGroup peptideGroup)
    {
        updatePrecursorRepresentativeState("pg.Id", peptideGroup.getId());
    }

    private static void updatePrecursorRepresentativeState(String refCol, int refId)
    {
        SQLFragment updatePrecursorStateSQL = new SQLFragment();
        updatePrecursorStateSQL.append("UPDATE "+TargetedMSManager.getTableInfoPrecursor());
        updatePrecursorStateSQL.append(" SET RepresentativeDataState = pg.RepresentativeDataState ");
        updatePrecursorStateSQL.append(" FROM ");
        updatePrecursorStateSQL.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg");
        updatePrecursorStateSQL.append(", ");
        updatePrecursorStateSQL.append(TargetedMSManager.getTableInfoPeptide(), "pep");
        updatePrecursorStateSQL.append(" WHERE pg.Id = pep.peptideGroupId");
        updatePrecursorStateSQL.append(" AND pep.Id = "+TargetedMSManager.getTableInfoPrecursor()+".peptideId");
        updatePrecursorStateSQL.append(" AND " + refCol + " = ?");
        // Ignore decoy peptides
        updatePrecursorStateSQL.append(" AND ( pep.Decoy IS NULL ");
        updatePrecursorStateSQL.append(" OR ");
        updatePrecursorStateSQL.append( "pep.Decoy = " + TargetedMSManager.getSqlDialect().getBooleanFALSE());
        updatePrecursorStateSQL.append(") ");
        updatePrecursorStateSQL.add(refId);

        new SqlExecutor(TargetedMSManager.getSchema()).execute(updatePrecursorStateSQL);
    }

    private static int resolveRepresentativePeptideState(Container container, TargetedMSRun run)
    {
        String sqlFalse = TargetedMSManager.getSqlDialect().getBooleanFALSE();

        // Mark everything in this run that doesn't already have representative data in this container as being representative
        SQLFragment makeActiveSQL = new SQLFragment("UPDATE " + TargetedMSManager.getTableInfoPrecursor());
        makeActiveSQL.append(" SET RepresentativeDataState = ?");
        makeActiveSQL.add(RepresentativeDataState.Representative.ordinal());
        makeActiveSQL.append(" FROM " + TargetedMSManager.getTableInfoPeptide());
        makeActiveSQL.append(", "+TargetedMSManager.getTableInfoPeptideGroup());
        makeActiveSQL.append(", "+TargetedMSManager.getTableInfoRuns());
        makeActiveSQL.append(" WHERE " + TargetedMSManager.getTableInfoPeptideGroup() + ".RunId=? ");
        makeActiveSQL.add(run.getId());
        makeActiveSQL.append(" AND "+TargetedMSManager.getTableInfoPrecursor()+".PeptideId = "+TargetedMSManager.getTableInfoPeptide()+".Id");
        makeActiveSQL.append(" AND "+TargetedMSManager.getTableInfoPeptide()+".PeptideGroupId = "+TargetedMSManager.getTableInfoPeptideGroup()+".Id");
        // Ignore decoy peptides
        makeActiveSQL.append(" AND ( " + TargetedMSManager.getTableInfoPeptide() + ".Decoy IS NULL ");
        makeActiveSQL.append(" OR ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPeptide() + ".Decoy = " + sqlFalse);
        makeActiveSQL.append(") ");
        // Ignore standard (e.g. iRT) peptides
        makeActiveSQL.append(" AND " + TargetedMSManager.getTableInfoPeptide() + ".StandardType IS NULL");
        makeActiveSQL.append(" AND ");
        makeActiveSQL.append(TargetedMSManager.getSqlDialect().concatenate("ModifiedSequence", "CAST(Charge AS varchar)"));
        makeActiveSQL.append(" NOT IN (SELECT ");
        makeActiveSQL.append(TargetedMSManager.getSqlDialect().concatenate("ModifiedSequence", "CAST(Charge AS varchar)"));
        makeActiveSQL.append(" FROM ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPrecursor(), "prec1");
        makeActiveSQL.append(", ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPeptide(), "pep1");
        makeActiveSQL.append(", ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoPeptideGroup(), "pg1");
        makeActiveSQL.append(", ");
        makeActiveSQL.append(TargetedMSManager.getTableInfoRuns(), "r1");
        makeActiveSQL.append(" WHERE pg1.RunId = r1.Id ");
        makeActiveSQL.append(" AND");
        makeActiveSQL.append(" pep1.PeptideGroupId = pg1.Id");
        makeActiveSQL.append(" AND");
        makeActiveSQL.append(" prec1.PeptideId = pep1.Id");
        makeActiveSQL.append(" AND");
        makeActiveSQL.append(" r1.Container=?");
        makeActiveSQL.append(" AND");
        makeActiveSQL.append(" prec1.RepresentativeDataState=? ");
        makeActiveSQL.add(container);
        makeActiveSQL.add(RepresentativeDataState.Representative.ordinal());
        makeActiveSQL.append(")");
        new SqlExecutor(TargetedMSManager.getSchema()).execute(makeActiveSQL);

        // Mark all other precursors as being in the conflicted state
        SQLFragment makeConflictedSQL = new SQLFragment("UPDATE "+TargetedMSManager.getTableInfoPrecursor());
        makeConflictedSQL.append(" SET RepresentativeDataState = ?");
        makeConflictedSQL.add(RepresentativeDataState.Conflicted.ordinal());
        makeConflictedSQL.append(" FROM "+TargetedMSManager.getTableInfoPeptide());
        makeConflictedSQL.append(", "+TargetedMSManager.getTableInfoPeptideGroup());
        makeConflictedSQL.append(" WHERE " + TargetedMSManager.getTableInfoPeptideGroup() + ".RunId=?");
        makeConflictedSQL.add(run.getId());
        makeConflictedSQL.append(" AND " + TargetedMSManager.getTableInfoPrecursor() + ".RepresentativeDataState != ?");
        makeConflictedSQL.add(RepresentativeDataState.Representative.ordinal());
        // Ignore decoy peptides
        makeConflictedSQL.append(" AND ( " + TargetedMSManager.getTableInfoPeptide() + ".Decoy IS NULL ");
        makeConflictedSQL.append(" OR ");
        makeConflictedSQL.append(TargetedMSManager.getTableInfoPeptide() + ".Decoy = " + sqlFalse);
        makeConflictedSQL.append(") ");
        // Ignore standard (e.g. iRT) peptides
        makeConflictedSQL.append(" AND " + TargetedMSManager.getTableInfoPeptide() + ".StandardType IS NULL");
        makeConflictedSQL.append(" AND "+TargetedMSManager.getTableInfoPrecursor()+".PeptideId = "+TargetedMSManager.getTableInfoPeptide()+".Id");
        makeConflictedSQL.append(" AND "+TargetedMSManager.getTableInfoPeptide()+".PeptideGroupId = "+TargetedMSManager.getTableInfoPeptideGroup()+".Id");
        int conflictCount = new SqlExecutor(TargetedMSManager.getSchema()).execute(makeConflictedSQL);

        // Get a list of standard precursors in this run
        // Issue 23843: Include iRT peptides in chromatogram libraries
        // We do not want users to have to resolve conflicts for standard peptides.  The latest version of the
        // standard peptides will be marked as representative.
        // If there are standard peptides in this run, we will mark them as representative. Older versions of these
        // peptides, from previous runs, will be marked as deprecated.
        List<Integer> stdPrecursorIdsInRun = getStdPrecursorIdsInRun(run);
        if(stdPrecursorIdsInRun != null && stdPrecursorIdsInRun.size() > 0)
        {
            // Get a list of current representative precursors in the folder that have the same modified sequence and charge
            // as the standard precursors in the run.
            List<Integer> currentRepStdPrecursorIds = getCurrentStandardPrecursorIds(container, stdPrecursorIdsInRun);

            PrecursorManager.updateRepresentativeStatus(stdPrecursorIdsInRun, RepresentativeDataState.Representative);
            PrecursorManager.updateStatusToDeprecatedOrNotRepresentative(currentRepStdPrecursorIds);
        }

        return conflictCount;
    }
}
