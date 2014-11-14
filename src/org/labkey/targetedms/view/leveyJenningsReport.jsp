<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

/**
* User: cnathe
* Date: Sept 19, 2011
*/

%>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext3"));
        resources.add(ClientDependency.fromFilePath("targetedms/js/LeveyJenningsTrendPlotPanel.js"));
        resources.add(ClientDependency.fromFilePath("targetedms/LeveyJenningsReport.css"));
        return resources;
    }
%>

<table cellpadding="0" cellspacing="15">
    <tr>
        <td rowspan="2"><div id="graphParamsPanel"></div></td>
        <td><div id="guideSetOverviewPanel"></div></td>
    </tr>
    <tr>
        <td><div id="rPlotPanel"></div></td>
    </tr>
</table>
<div id="trackingDataPanelTitle" style="margin-left:15px"></div>
<div id="trackingDataPanel" style="margin-left:15px"></div>

<script type="text/javascript">

        var $h = Ext.util.Format.htmlEncode;

        // the default number of records to return for the report when no start and end date are provided
        var defaultRowSize = 30;

        function init()
        {
            LABKEY.Query.selectRows({
                schemaName: 'targetedms',
                queryName: 'peptidechrominfo',
                columns: "PeptideId/Sequence,SampleFileId/AcquisitionTime",
                maxRows: 1,
                success: function(data) {
                    if (data.rows.length == 0)
                        Ext.get('graphParamsPanel').update("Error: there were no records found.");
                    initializeReportPanels();
                },
                failure: function(response) {
                    Ext.get('graphParamsPanel').update("Error: " + response.exception);
                }
            });
        }

        function initializeReportPanels()
        {
            // initialize the panel that displays the R plot for the trend plotting of EC50, AUC, and High MFI
            var trendPlotPanel = new LABKEY.LeveyJenningsTrendPlotPanel({
                renderTo: 'rPlotPanel',
                cls: 'extContainer',
                listeners: {
                    'reportFilterApplied': function (startDate, endDate)
                    {
//                        trackingDataPanel.graphParamsSelected(startDate, endDate);
                    },
                    'togglePdfBtn': function (toEnable)
                    {
//                        guideSetPanel.toggleExportBtn(toEnable);
                    }
                }
            });
        }

        Ext.onReady(init);
</script>