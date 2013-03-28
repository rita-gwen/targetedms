<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.targetedms.TargetedMSController" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.targetedms.search.ModificationSearchBean" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      return resources;
  }
%>
<%
    JspView<ModificationSearchBean> me = (JspView<ModificationSearchBean>) HttpView.currentView();
    ModificationSearchBean bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();

    String initSearchType = bean.getForm().getSearchType() != null ? bean.getForm().getSearchType() : "deltaMass";
    String initAminoAcids = bean.getForm().getAminoAcids() != null ? bean.getForm().getAminoAcids() : "";
    Double initDeltaMass = bean.getForm().getDeltaMass() != null ? bean.getForm().getDeltaMass() : null;
    String initNameType = bean.getForm().getModificationNameType() != null ? bean.getForm().getModificationNameType() : "custom";
    boolean initStructuralCheck = (bean.getForm().isStructural() != null && bean.getForm().isStructural()) || initSearchType.equals("deltaMass");
    boolean initIsotopeLabelCheck = (bean.getForm().isIsotopeLabel() != null && bean.getForm().isIsotopeLabel()) || initSearchType.equals("deltaMass");

    ActionURL modificationSearchUrl = new ActionURL(TargetedMSController.ModificationSearchAction.class, getViewContext().getContainer());

    String renderId = "modification-search-form-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id=<%=q(renderId)%>></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        // model used to parse unimod.xml for each modification
        Ext4.define('UnimodRecord', {
            extend: 'Ext.data.Model',
            fields: [
                { name: 'title', mapping: '@title' },
                { name: 'full_name', mapping: '@full_name' },
                { name: 'mono_mass', mapping: 'delta@mono_mass' },
                { name: 'avge_mass', mapping: 'delta@avge_mass' }
            ]
        });

        // model used to parse the specified sites for a given unimod modification name
        Ext4.define('UnimodSpecificity', {
            extend: 'Ext.data.Model',
            fields: [
                { name: 'site', mapping: '@site' },
                { name: 'position', mapping: '@position' },
                { name: 'hidden', mapping: '@hidden' },
                { name: 'classification', mapping: '@classification' }
            ]
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: <%=q(renderId)%>,
            standardSubmit: true,
            border: false, frame: false,
            defaults: {
                labelWidth: 120,
                labelStyle: 'background-color: #E1E5E1; padding: 5px;'
            },
            items: [
                {
                    xtype: 'radiogroup',
                    fieldLabel: 'Search By',
                    columns: 2,
                    width: 430,
                    items: [
                        { boxLabel: 'Delta Mass', name: 'searchType', inputValue: 'deltaMass', checked: <%=initSearchType.equals("deltaMass")%> },
                        { boxLabel: 'Modification Name', name: 'searchType', inputValue: 'modificationName', checked: <%=initSearchType.equals("modificationName")%>}
                    ],
                    listeners: {
                        scope: this,
                        change: function(cmp, newValue, oldValue) {
                            if (newValue['searchType'])
                            {
                                // hide/show form fields based on the selected search type
                                form.down('textfield[name=aminoAcids]').setVisible(newValue['searchType'] == 'deltaMass');
                                form.down('numberfield[name=deltaMass]').setVisible(newValue['searchType'] == 'deltaMass');
                                form.down('radiogroup[name=modificationNameTypeRadioGroup]').setVisible(newValue['searchType'] == 'modificationName');
                                form.down('checkboxgroup[name=includeCheckboxGroup]').setVisible(newValue['searchType'] == 'modificationName');

                                // hide/show name combos based on searchType and modificationNameType
                                var modificationNameType = form.down('radiogroup[name=modificationNameTypeRadioGroup]').getValue()["modificationNameType"];
                                form.down('combo[name=customName]').setVisible(newValue['searchType'] == 'modificationName' && modificationNameType == 'custom');
                                form.down('combo[name=unimodName]').setVisible(newValue['searchType'] == 'modificationName' && modificationNameType != 'custom');

                                // clear values for text/number fields and combos
                                form.down('textfield[name=aminoAcids]').setValue(null);
                                form.down('textfield[name=aminoAcids]').clearInvalid();
                                form.down('numberfield[name=deltaMass]').setValue(null);
                                form.down('numberfield[name=deltaMass]').clearInvalid();
                                form.down('combo[name=customName]').setValue(null);
                                form.down('combo[name=unimodName]').setValue(null);
                            }
                        }
                    }
                },
                {
                    xtype: 'textfield',
                    fieldLabel: 'Amino Acids',
                    name: 'aminoAcids',
                    allowBlank: false,
                    hidden: <%=!initSearchType.equals("deltaMass")%>,
                    value: <%=q(initAminoAcids)%>
                },
                {
                    xtype: 'numberfield',
                    fieldLabel: 'Delta Mass',
                    name: 'deltaMass',
                    hideTrigger: true,
                    allowBlank: false,
                    allowDecimal: true,
                    decimalPrecision: 1, // round valeus to tenth
                    hidden: <%=!initSearchType.equals("deltaMass")%>,
                    value: '<%=initDeltaMass%>'
                },
                {
                    xtype: 'radiogroup',
                    fieldLabel: 'Type',
                    columns: 1,
                    name: 'modificationNameTypeRadioGroup',
                    hidden: <%=!initSearchType.equals("modificationName")%>,
                    items: [
                        { boxLabel: 'Names used in imported experiments', name: 'modificationNameType', inputValue: 'custom', checked: <%=initNameType.equals("custom")%> },
                        { boxLabel: 'Common Unimod modifications', name: 'modificationNameType', inputValue: 'unimodCommon', checked: <%=initNameType.equals("unimodCommon")%> },
                        { boxLabel: 'All Unimod modifications', name: 'modificationNameType', inputValue: 'unimodAll', checked: <%=initNameType.equals("unimodAll")%> }
                    ],
                    listeners: {
                        change : function(cmp, newValue) {
                            var value = newValue["modificationNameType"];

                            // hide/show name combos based on radio selection
                            form.down('combo[name=customName]').setVisible(value == "custom");
                            form.down('combo[name=unimodName]').setVisible(value == "unimodCommon" || value == "unimodAll");

                            // clear combo values on radio change
                            form.down('combo[name=customName]').setValue(null);
                            form.down('combo[name=unimodName]').setValue(null);

                            // filter unimod combo based on Common or All selection
                            // TODO: filter combo store
                        }
                    }
                },
                {
                    xtype: 'checkboxgroup',
                    width: 430,
                    fieldLabel: 'Include',
                    name: 'includeCheckboxGroup',
                    hidden: <%=!initSearchType.equals("modificationName")%>,
                    items: [
                        { boxLabel: 'Structural', name: 'structural', inputValue: true, checked: <%=initStructuralCheck%> },
                        { boxLabel: 'Isotope Label', name: 'isotopeLabel', inputValue: true, checked: <%=initIsotopeLabelCheck%> }
                    ],
                    listeners: {
                        change: function(cmp, newValue, oldValue) {
                            // filter the custom name store based on the selected types
                            var customNameCombo = form.down('combo[name=customName]');
                            form.filterComboStore(customNameCombo.getStore(), newValue);

                            // TODO: enable
                            // filter the unimod name store based on the selected types
                            //var unimodNameCombo = form.down('combo[name=unimodName]');
                            //form.filterComboStore(unimodNameCombo.getStore(), newValue);
                        }
                    }
                },
                {
                    xtype: 'combo',
                    fieldLabel: 'Custom Name',
                    name: 'customName',
                    width: 500,
                    hidden: <%=!initSearchType.equals("modificationName") || !initNameType.equals("custom")%>,
                    value: <%=q(bean.getForm().getCustomName())%>,
                    editable : true,
                    queryMode : 'local',
                    displayField : 'Name',
                    valueField : 'Name',
                    store : Ext4.create('LABKEY.ext4.Store', {
                        schemaName: "targetedms",
                        // union of structural and isotope label modifications
                        sql : "SELECT CONVERT('Structural', SQL_VARCHAR) AS Type, Name, GROUP_CONCAT(AminoAcid, '') AS AminoAcid, MassDiff FROM ("
                        	+ "  SELECT DISTINCT"
                            + "    StructuralModId.Name AS Name,"
                            + "    CASE WHEN StructuralModId.AminoAcid IS NOT NULL THEN StructuralModId.AminoAcid"
                            + "         WHEN IndexAA IS NOT NULL THEN substring(PeptideId.Sequence, IndexAA + 1, 1)"
                            + "    END AS AminoAcid,"
                            + "    MassDiff"
                            + "  FROM PeptideStructuralModification"
                            + ") AS x GROUP BY Name, MassDiff "
                            + "UNION "
                            + "SELECT CONVERT('Isotope Label', SQL_VARCHAR) AS Type, Name, GROUP_CONCAT(AminoAcid, '') AS AminoAcid, MassDiff FROM ("
                            + "  SELECT DISTINCT"
                            + "    IsotopeModId.Name AS Name,"
                            + "    CASE WHEN IsotopeModId.AminoAcid IS NOT NULL THEN IsotopeModId.AminoAcid"
                            + "         WHEN IndexAA IS NOT NULL THEN substring(PeptideId.Sequence, IndexAA + 1, 1)"
                            + "    END AS AminoAcid,"
                            + "    MassDiff"
                            + "  FROM PeptideIsotopeModification"
                            + ") AS x GROUP BY Name, MassDiff",
                        sort: "Name",
                        autoLoad: true,
                        listeners: {
                            load: function(store, records) {
                                // set initial filter
                                form.filterComboStore(store, form.down('checkboxgroup[name=includeCheckboxGroup]').getValue());
                            }
                        }
                    }),
                    listeners: {
                        change: function(cmp, newValue, oldValue) {
                            // set the amino acid and delta mass based on the selected custom name record
                            var record = cmp.getStore().findRecord('Name', newValue);
                            form.down('textfield[name=aminoAcids]').setValue(record ? record.get('AminoAcid') : null);
                            form.down('numberfield[name=deltaMass]').setValue(record ? record.get('MassDiff') : null);
                        }
                    }
                },
                {
                    xtype: 'combo',
                    fieldLabel: 'Unimod Name:<%= helpPopup("Unimod", "Unimod is a public domain database, distributed under a copyleft licence: a copyright notice that permits unrestricted redistribution and modification, provided that all copies and derivatives retain the same permissions.") %>',
                    labelSeparator: '',
                    name: 'unimodName',
                    width: 500,
                    hidden: <%=!initSearchType.equals("modificationName") || initNameType.equals("custom")%>,
                    value: <%=q(bean.getForm().getUnimodName())%>,
                    editable : true,
                    queryMode : 'local',
                    displayField : 'title',
                    valueField : 'title',
                    store: Ext4.create('Ext.data.Store', {
                        model: 'UnimodRecord',
                        autoLoad: true,
                        proxy: {
                            type: 'ajax',
                            url : LABKEY.contextPath + '/TargetedMS/unimod/unimod.xml',
                            reader: {
                                type: 'xml',
                                //namespace: 'umod',
                                root: 'modifications',
                                record: 'mod'
                            }
                        }
                    }),
                    listeners: {
                        scope: this,
                        change: function(cmp, newValue, oldValue) {
                            // set the amino acid and delta mass based on the selected unimod name record
                            var record = cmp.getStore().findRecord('title', newValue);
                            form.down('textfield[name=aminoAcids]').setValue(null);
                            form.down('numberfield[name=deltaMass]').setValue(record ? record.get('mono_mass') : null);

                            // parse the XML file again for the selected modification name to get the set of specified sites
                            // note: skipping C-term and N-term
                            var modSpecificityStore = Ext4.create('Ext.data.Store', {
                                model: 'UnimodSpecificity',
                                autoLoad: true,
                                proxy: {
                                    type: 'ajax',
                                    url : LABKEY.contextPath + '/TargetedMS/unimod/unimod.xml',
                                    reader: {
                                        type: 'xml',
                                        //namespace: 'umod',
                                        root: 'mod[title=' + newValue + ']',
                                        record: 'specificity'
                                    }
                                },
                                listeners: {
                                    scope: this,
                                    load: function(store, records) {
                                        if (records.length > 0)
                                        {
                                            var aminoAcidStr = "";
                                            Ext4.each(records, function(record) {
                                                if (record.get("site") != null && record.get("site").length == 1)
                                                    aminoAcidStr += record.get("site");
                                            });
                                            form.down('textfield[name=aminoAcids]').setValue(aminoAcidStr);
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            ],
            buttonAlign: 'left',
            buttons: [{
                text: 'Search',
                formBind: true,
                handler: function(btn) {
                    var values = form.getForm().getValues();
                    if (values['aminoAcids'] && values['deltaMass'])
                    {
                        form.submit({
                            url: <%=q(modificationSearchUrl.getLocalURIString())%>,
                            method: 'GET',
                            params: values
                        });
                    }
                }
            }],

            filterComboStore : function(store, values) {
                store.clearFilter();
                store.filterBy(function(record) {
                    return (values["structural"] && record.get("Type") == "Structural")
                        || (values["isotopeLabel"] && record.get("Type") == "Isotope Label");
                });
            }
        });
    });

</script>


