package org.labkey.targetedms.chromlib;

import org.labkey.targetedms.chromlib.Constants.StructuralModLossColumn;
import org.labkey.targetedms.chromlib.Constants.StructuralModificationColumn;
import org.labkey.targetedms.chromlib.Constants.Table;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * User: vsharma
 * Date: 1/2/13
 * Time: 10:15 PM
 */
public class LibStructuralModificationDao extends BaseDaoImpl<LibStructuralModification>
{
    private final Dao<LibStructuralModLoss> _modLossDao;

    public LibStructuralModificationDao(Dao<LibStructuralModLoss> modLossDao)
    {
        _modLossDao = modLossDao;
    }

    public void save(LibStructuralModification structuralMod, Connection connection) throws SQLException
    {
        if(structuralMod != null)
        {
            super.save(structuralMod, connection);

            if(_modLossDao != null)
            {
                for(LibStructuralModLoss modLoss: structuralMod.getModLosses())
                {
                    modLoss.setStructuralModId(structuralMod.getId());
                    _modLossDao.save(modLoss, connection);
                }
            }
        }
    }

    @Override
    protected void setValuesInStatement(LibStructuralModification structuralMod, PreparedStatement stmt) throws SQLException
    {
        int colIndex = 1;
        stmt.setString(colIndex++, structuralMod.getName());
        stmt.setObject(colIndex++, structuralMod.getAminoAcid(), Types.VARCHAR);
        stmt.setObject(colIndex++, structuralMod.getTerminus(), Types.CHAR);
        stmt.setObject(colIndex++, structuralMod.getFormula(), Types.VARCHAR);
        stmt.setObject(colIndex++, structuralMod.getMassDiffMono(), Types.DOUBLE);
        stmt.setObject(colIndex++, structuralMod.getMassDiffAvg(), Types.DOUBLE);
        stmt.setObject(colIndex++, structuralMod.getUnimodId(), Types.INTEGER);
        stmt.setBoolean(colIndex++, structuralMod.getVariable());
        stmt.setObject(colIndex, structuralMod.getExplicitMod(), Types.BOOLEAN);
    }

    @Override
    public String getTableName()
    {
        return Table.StructuralModification.name();
    }

    @Override
    protected Constants.ColumnDef[] getColumns()
    {
        return StructuralModificationColumn.values();
    }

    @Override
    public List<LibStructuralModification> queryForForeignKey(String foreignKeyColumn, int foreignKeyValue, Connection connection) throws SQLException
    {
        throw new UnsupportedOperationException(getTableName()+" does not have a foreign key");
    }

    protected List<LibStructuralModification> parseQueryResult(ResultSet rs) throws SQLException
    {
        List<LibStructuralModification> structuralMods = new ArrayList<LibStructuralModification>();
        while(rs.next())
        {
            LibStructuralModification structuralModification = new LibStructuralModification();
            structuralModification.setId(rs.getInt(StructuralModificationColumn.Id.colName()));
            structuralModification.setName(rs.getString(StructuralModificationColumn.Name.colName()));
            structuralModification.setAminoAcid(rs.getString(StructuralModificationColumn.AminoAcid.colName()));
            String terminus = rs.getString(StructuralModificationColumn.Terminus.colName());
            if(terminus != null) structuralModification.setTerminus(terminus.charAt(0));
            structuralModification.setFormula(rs.getString(StructuralModificationColumn.Formula.colName()));
            double massDiffMono = rs.getDouble(StructuralModificationColumn.MassDiffMono.colName());
            if(!rs.wasNull())
                structuralModification.setMassDiffMono(massDiffMono);
            double massDiffAvg = rs.getDouble(StructuralModificationColumn.MassDiffAvg.colName());
            if(!rs.wasNull())
                structuralModification.setMassDiffAvg(massDiffAvg);
            int unimodId = rs.getInt(StructuralModificationColumn.UnimodId.colName());
            if(!rs.wasNull())
                structuralModification.setUnimodId(unimodId);
            structuralModification.setVariable(rs.getBoolean(StructuralModificationColumn.Variable.colName()));
            boolean explicitMod = rs.getBoolean(StructuralModificationColumn.ExplicitMod.colName());
            if(!rs.wasNull())
                structuralModification.setExplicitMod(explicitMod);
            structuralMods.add(structuralModification);
        }
        return structuralMods;
    }

    public void loadStructuralModLosses(LibStructuralModification strMod, Connection connection) throws SQLException
    {
        if(strMod != null)
        {
            List<LibStructuralModLoss> modLosses = _modLossDao.queryForForeignKey(StructuralModLossColumn.StructuralModId.colName(),
                                                                                  strMod.getId(),
                                                                                  connection);
            for(LibStructuralModLoss modLoss: modLosses)
            {
                strMod.addModLoss(modLoss);
            }
        }
    }

    @Override
    public void saveAll(List<LibStructuralModification> structuralModifications, Connection connection) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

}
