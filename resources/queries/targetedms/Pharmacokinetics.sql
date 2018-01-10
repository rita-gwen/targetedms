/*
 * Copyright (c) 2017 LabKey Corporation
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
SELECT
  sub.PeptideId,
  sub.MoleculeId,
  sub.Time,
  AVG(sub.calculatedConcentration)          AS Concentration,
  group_concat(sub.calculatedConcentration) AS Concentrations,
  MAX(sub.Sequence)                         AS Peptide,
  MAX(sub.Filename)                         AS FileName
FROM
  (
    SELECT
      ci.PeptideId,
      ci.MoleculeId,
      ifdefined(rep.Time) AS Time,
      ci.calculatedConcentration,
      pep.sequence,
      rep.runid.filename
    FROM

      generalmoleculechrominfo ci
      JOIN samplefile sf ON sf.id = ci.samplefileid
      JOIN replicate rep ON rep.id = sf.replicateid
      JOIN peptide pep ON pep.id = ci.peptideid
    WHERE (ci.SampleFileId.ReplicateId.SampleType IS NULL OR lower(ci.SampleFileId.ReplicateId.SampleType) = 'unknown')
          AND ifdefined(rep.Time) IS NOT NULL) sub
GROUP BY sub.PeptideId, sub.MoleculeId, sub.Time