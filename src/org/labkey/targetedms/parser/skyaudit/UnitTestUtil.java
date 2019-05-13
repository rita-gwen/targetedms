package org.labkey.targetedms.parser.skyaudit;

import org.apache.commons.io.FileExistsException;
import org.apache.log4j.Logger;
import org.labkey.api.data.BaseSelector;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.writer.ZipUtil;
import org.labkey.targetedms.SkylineFileUtils;
import org.labkey.targetedms.TargetedMSManager;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class UnitTestUtil
{

    private static String[] _samplePath = {"sampledata", "TargetedMS"};
    private static String[] _resourcePath = {"server", "customModules", "targetedms", "resources"};

    public static File getSampleDataFile(String p_fileName) throws UnsupportedEncodingException
    {

        return getTestFile(p_fileName, UnitTestUtil._samplePath);
    }

    public static File getResourcesFile(String p_fileName) throws UnsupportedEncodingException
    {
        return getTestFile(p_fileName, UnitTestUtil._resourcePath);
    }

    private static File getTestFile(String p_fileName, String[] p_pathList) throws UnsupportedEncodingException{

        //we get location of the current class file, trace it up tp the LabKey folder (which is
        //not good for production code but for testing util it should be fine) and append the
        //known path and file name to the Labkey root.
        URL r = UnitTestUtil.class.getResource("/");
        String decodedUrl = "";
        decodedUrl = URLDecoder.decode(r.getFile(), "UTF-8");
        if(decodedUrl.startsWith("/"))
            decodedUrl = decodedUrl.replaceFirst("/", "");

        //java.nio.file.Path currentDir = new File(System.getProperty("user.dir")).toPath();
        java.nio.file.Path currentDir = new File(decodedUrl).toPath();
        while(!currentDir.getFileName().toString().toLowerCase().equals("build"))
            currentDir = currentDir.getParent();
        currentDir = currentDir.getParent();
        String[] pathComponents = new String[] {currentDir.toString(), String.join(File.separator, p_pathList), p_fileName};
        File result = new File(String.join(File.separator, pathComponents));
        if(result.exists() && result.isFile())
            return result;
        else
            return null;
    }

    //delete testing records from the database;
    public static void cleanupDatabase(GUID p_documentGUID){

        TableInfo entryTbl = TargetedMSManager.getTableInfoSkylineAuditLogEntry();
        Filter entryFilter = new SimpleFilter(FieldKey.fromParts("documentGuid"), p_documentGUID.toString());
        //retrieving entry record Ids based on the testing document GUID
        SQLFragment query = Table.getSelectSQL(entryTbl, entryTbl.getPkColumns(), entryFilter, null);
        BaseSelector.ResultSetHandler<List<Integer>> resultSetHandler = (rs, conn) -> {
            List<Integer> idsList = new ArrayList<>(10);
            while(rs.next()){
                idsList.add(rs.getInt(1));
            }
            return  idsList;
        };
        List<Integer> recordIds = new SqlExecutor(entryTbl.getSchema()).executeWithResults(query, resultSetHandler);

        //deleting entries messages
        SimpleFilter messageDeleteFilter = new SimpleFilter();
        messageDeleteFilter.addInClause(FieldKey.fromParts("entryId"), recordIds);

        Table.delete(TargetedMSManager.getTableInfoSkylineAuditLogMessage(), messageDeleteFilter);

        //deleting the entries
        Table.delete(TargetedMSManager.getTableInfoSkylineAuditLogEntry(), entryFilter);
    }

    public static File extractLogFromZip(File p_zip, Logger p_logger) throws IOException
    {
        File workingDir = new File(System.getProperty(SkylineAuditLogParser.TestCase.SYS_PROPERTY_CWD) + "/temp");
        if(!workingDir.exists())
            throw new FileExistsException("Cannot get a working dir for testing");
        File zipDir = new File(workingDir, SkylineFileUtils.getBaseName(p_zip.getName()));

        if (zipDir.exists())
        {
            String[] children = zipDir.list();
            boolean res = false;
            for(String child : children){
                File f = new File(zipDir, child);
                if(f.exists())
                    res = f.delete();
            }
        }
        List<File> files = ZipUtil.unzipToDirectory(p_zip, zipDir, p_logger);
        for(File file : files){
            String ext = FileUtil.getExtension(file.getName());
            if(SkylineAuditLogParser.TestCase.SKYLINE_LOG_EXTENSION.equals(ext))
                return file;
        }
        return null;
    }
}