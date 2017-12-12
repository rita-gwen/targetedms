

<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("targetedms/css/FiguresOfMerit.css");
        dependencies.add("internal/jQuery");
    }
%>

<div class="container-fluid targetedms-fom">
    <div id="targetedms-fom-export" class="export-icon" data-toggle="tooltip" title="Export to Excel">
        <i class="fa fa-file-excel-o" onclick="exportExcel()"></i>
    </div>
    <h3 id="fom-title1"></h3>
    <h4 id="fom-title2"></h4>
    <h4 id="fom-title3"></h4>
    <br>
    <h4 id="standard-title2"></h4>
    <hr>
    <table id="fom-table-standard" class="table table-striped table-responsive fom-table">
        <thead id="standard-header" />
        <tbody id="standard-body" />
    </table>
    <div id="bias-limit"></div>
    <div id="loq-stat"></div>
    <div id="uloq-stat"></div>
    <br>
    <h4 id="qc-title2"></h4>
    <hr>
    <table id="fom-table-qc" class="table table-striped table-responsive fom-table">
        <thead id="qc-header" />
        <tbody id="qc-body" />
    </table>

</div>

<script type="text/javascript">

    +function($) {
        this.$ = $;

        var params = LABKEY.ActionURL.getParameters();

        this.moleculeId = params['GeneralMoleculeId'];
        this.biasLimit = 25;  // percent
        this.xlsExport = [];

        this.exportExcel = function() {
            LABKEY.Utils.convertToExcel({
                fileName : this.file.split(".")[0] + '_' + this.title + '.xlsx',
                sheets:
                        [
                            {
                                name: 'Sheet1',
                                data: this.xlsExport
                            }
                        ]
            });
        };

        var displayHeader = function() {
            $('#fom-title1').html(this.title);
            $('#fom-title2').html("Skyline File: " + this.file);
            $('#fom-title3').html("Sample(s): " + this.sample);

            var title2 = "Concentrations";
            if(this.sampleType === 'qc') {
                title2 = "Concentrations in Quality Control";
            }
            else if(this.sampleType === 'standard') {
                title2 = "Concentrations in Standards"
            }

            $('#' + this.sampleType + '-title2').html(title2);
            this.xlsExport.push([]);
            this.xlsExport.push([title2]);

            var colHdrs = [];
            var hdr = "";
            if (this.hdrLabels == null || this.hdrLabels.length < 1) {
                $('#' + this.sampleType + '-header').html("No data of this type");
                colHdrs.push("No data of this type");
                hdr += "No data of this type";
            }
            else {
                colHdrs.push("");
                hdr += '<tr><td>Concentration</td>';
                var units = '';

                if (this.Units)
                    units = ' (' + this.Units + ')';

                this.hdrLabels.forEach(function (label) {
                    hdr += '<td>' + label + units + '</td>' + '<td>Bias (%)</td>';
                    colHdrs.push(label + units);
                    colHdrs.push("Bias (%)");
                }, this);
                hdr += '</tr>';
            }

            $('#' + this.sampleType + '-header').html(hdr);
            this.xlsExport.push(colHdrs);
        };

        var displayBody = function() {
            if (this.hdrLabels != null && this.hdrLabels.length > 0) {
                var html = getRawDataHtml();
                html += getSummaryHtml();
                $('#' + this.sampleType + '-body').html(html);
            }
        };

        var getSummaryHtml = function() {

            var stats = ["n", "Mean", "StdDev", "CV", "Bias"]
            var html = "", exportRow;

            stats.forEach(function(stat) {
                html += '<tr><td>' + stat + '</td>';
                exportRow = [stat];
                this.hdrLabels.forEach(function(col) {
                    if (stat === "n") {
                        html += '<td>' + (this.rawData[col].length?this.rawData[col].length:"") + '</td><td></td>';
                        exportRow.push(this.rawData[col].length?this.rawData[col].length:"");
                        exportRow.push("");
                    }
                    else {
                        html += '<td>' + this.summaryData[col][stat] + '</td><td></td>';
                        exportRow.push(Number(this.summaryData[col][stat]));
                        exportRow.push("");
                    }
                }, this);
                html += '</tr>';
                this.xlsExport.push(exportRow);
            }, this);

            return html;
        };

        var getRawDataHtml = function() {
            var index = 0;
            var found;
            var html = "", exportRow;
            do {
                found = false;
                html += "<tr><td></td>";

                exportRow = [""];

                this.hdrLabels.forEach(function (label) {
                    if (this.rawData[label][index]) {
                        found = true;
                        html += "<td>" + this.rawData[label][index].value + "</td><td>" + this.rawData[label][index].bias + "</td>";
                        exportRow.push(Number(this.rawData[label][index].value));
                        exportRow.push(Number(this.rawData[label][index].bias));
                    }
                    else {
                        html += "<td></td><td></td>";
                        exportRow.push("");
                        exportRow.push("");
                    }
                }, this);

                html += "</tr>";
                index++;
                this.xlsExport.push(exportRow);

            } while (found);

            return html;
        };

        var parseMetadata = function(data) {
            var title = "";
            var sample = "";
            var file = "";

            if(data.rowCount === 0) {
                title = "Molecule"
            }
            else {
                var sampleNames = [];
                sample = data.rows.reduce(function(accum, row) {
                    if (row.SampleName != null && sampleNames.indexOf(row.SampleName) === -1) {
                        if (accum.length !== 0)
                            accum += ", ";

                        accum += row.SampleName;
                    }
                    return accum;
                }, "");

                if (data.rows[0].FileName != null) {
                    file = data.rows[0].FileName;
                }

                if (data.rows[0].PeptideName != null) {
                    title = "Peptide: " + data.rows[0].PeptideName;
                }
                else if (data.rows[0].MoleculeName != null) {
                    title = "Molecule: " + data.rows[0].MoleculeName;
                }
                else {
                    title = "Molecule ID: " + this.moleculeId;
                }

                // Get units
                if (data.rows[0].Units != null)
                    this.Units = data.rows[0].Units;
            }

            if( this.title == null || this.title === "Molecule") {
                this.title = title;
            }

            if( this.sample == null )
                this.sample = sample;

            if( this.file == null )
                this.file = file;

            // Add title to export
            if (this.xlsExport.length < 1) {
                this.xlsExport.push([this.title]);
                this.xlsExport.push(["Sample(s): " + this.sample]);
            }
        };

        var parseRawData = function(data) {

            // No data
            if (data.rows.length < 1)
                return;

            parseMetadata(data);

            // Process data to collapse rows
            data.rows.forEach(function (row) {
                var colName, replicate;
                for (var col in row) {
                    if (row.hasOwnProperty(col)) {
                        colName = col.split('::')[0];
                        replicate = col.split('::')[1];
                        if (replicate != null && colName !== 'NULL') {
                            if (row[col] != null) {
                                if (!this.rawData[colName]) {
                                    this.rawData[colName] = [];
                                    this.hdrLabels.push(colName);  // Preserve order
                                }

                                this.rawData[colName].push({
                                    'value': row[col].toFixed(2),
                                    'bias': row['Bias'].toFixed(2)
                                })
                            }
                        }
                    }
                }
            }, this);

            // Sort columns
            this.hdrLabels.sort(function(a, b){return a-b;});
        };

        var parseSummaryData = function (data) {

            // Process data to collapse rows
            data.rows.forEach(function (row) {
                var colName, rowName;
                for (var col in row) {
                    if (row.hasOwnProperty(col)) {
                        colName = col.split("::")[0];
                        rowName = col.split("::")[1];
                        if (colName !== "NULL" && rowName != null) {
                            if (!this.summaryData[colName]) {
                                this.summaryData[colName] = {};
                            }

                            if (row[col] != null) {
                                this.summaryData[colName][rowName] = row[col].toFixed(2);
                            }
                            else {
                                this.summaryData[colName][rowName] = "";
                            }
                        }
                    }
                }
            }, this);
        };

        this.handleData = function () {

            displayHeader();
            displayBody();

            if (this.sampleType === 'standard')
                createLoqStats();

            if (this.callback != null)
                this.callback();
        };

        var createFomTable = function(sampleType, callback)
        {
            this.rawData = {};
            this.summaryData = {};
            this.hdrLabels = [];

            this.sampleType = sampleType;
            this.callback = callback;
            var multi = new LABKEY.MultiRequest();

            multi.add(LABKEY.Query.selectRows, {
                schemaName: 'targetedms',
                queryName: 'FiguresOfMeritPivot',
                filterArray: [LABKEY.Filter.create('RunId', params['RunId']),
                    LABKEY.Filter.create('MoleculeId', params['GeneralMoleculeId']),
                    LABKEY.Filter.create('SampleType', sampleType)
                ],
                scope: this,
                success: function (data) {
                    parseRawData(data);
                },
                failure: function (response) {
                    LABKEY.Utils.alert(response);
                }
            });

            multi.add(LABKEY.Query.selectRows, {
                schemaName: 'targetedms',
                queryName: 'FiguresOfMeritSummary',
                filterArray: [LABKEY.Filter.create('RunId', params['RunId']),
                    LABKEY.Filter.create('MoleculeId', params['GeneralMoleculeId']),
                    LABKEY.Filter.create('SampleType', sampleType)
                ],
                scope: this,
                success: function (data) {
                    parseSummaryData(data);
                },
                failure: function (response) {
                    LABKEY.Utils.alert(response);
                }
            });

            multi.send(this.handleData, this);
        };

        this.createLoqStats = function() {
            var loq = 'NA', uloq = 'NA', bias;
            this.hdrLabels.forEach(function(label, index) {
                bias = Math.abs(Number(this.summaryData[label]["Bias"]));
                if (bias <= this.biasLimit) {
                    if (loq === 'NA') {
                        loq = label;
                    }
                    else {
                        uloq = label;
                    }
                }
            }, this);

            $('#bias-limit').html('Bias Limit: ' + this.biasLimit + '%');
            $('#loq-stat').html('LOQ: ' + loq + ' ' + (this.Units?this.Units:''));
            $('#uloq-stat').html('ULOQ: ' + uloq + ' ' + (this.Units?this.Units:''));

            this.xlsExport.push([]);
            this.xlsExport.push(['Bias Limit: ' + this.biasLimit + '%']);
            this.xlsExport.push(['LOQ: ' + loq + ' ' + (this.Units?this.Units:'')]);
            this.xlsExport.push(['ULOQ: ' + uloq + ' ' + (this.Units?this.Units:'')]);
        };

        var createQcFomTable = function(callback) {
            createFomTable('qc', callback);
        };

        var createStandardFomTable = function(callback) {
            createFomTable('standard', callback);
        };

        createStandardFomTable(createQcFomTable);

    }(jQuery);


</script>

