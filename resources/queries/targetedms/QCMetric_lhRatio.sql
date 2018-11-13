/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
  COALESCE(PrecursorChromInfoId.PrecursorId.Id, PrecursorChromInfoId.MoleculePrecursorId.Id) AS PrecursorId,
  PrecursorChromInfoId.Id AS PrecursorChromInfoId,
  PrecursorChromInfoId.SampleFileId AS SampleFileId,
  COALESCE(PrecursorChromInfoId.PrecursorId.ModifiedSequence, PrecursorChromInfoId.MoleculePrecursorId.CustomIonName) AS SeriesLabel,
  CASE WHEN PrecursorChromInfoId.PrecursorId.Id IS NOT NULL THEN 'Peptide' ELSE 'Fragment' END AS DataType,
  AreaRatio AS MetricValue,
  COALESCE(PrecursorChromInfoId.PrecursorId.Mz, PrecursorChromInfoId.MoleculePrecursorId.Mz) AS mz
FROM PrecursorAreaRatio