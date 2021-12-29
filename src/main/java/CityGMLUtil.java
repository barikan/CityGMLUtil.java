import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.building.Building;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.citygml.core.CityObjectMember;
import org.citygml4j.model.citygml.generics.AbstractGenericAttribute;
import org.citygml4j.model.citygml.generics.IntAttribute;
import org.citygml4j.model.citygml.generics.MeasureAttribute;
import org.citygml4j.model.citygml.generics.StringAttribute;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurface;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurfaceProperty;
import org.citygml4j.model.gml.geometry.primitives.*;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityGMLUtil {


    public static void readCityGML(File f, List<Map<String, Object>> list) throws CityGMLBuilderException, CityGMLReadException {
        CityGMLContext ctx = CityGMLContext.getInstance();
        CityGMLBuilder builder = ctx.createCityGMLBuilder();
        CityGMLInputFactory in = builder.createCityGMLInputFactory();
        CityGMLReader reader = in.createCityGMLReader(f);
        while (reader.hasNext()) {
            CityGML citygml = reader.nextFeature();
            if (citygml.getCityGMLClass() == CityGMLClass.CITY_MODEL) {
                CityModel cityModel = (CityModel) citygml;
                for (CityObjectMember cityObjectMember : cityModel.getCityObjectMember()) {
                    AbstractCityObject cityObject = cityObjectMember.getCityObject();
                    if (cityObject.getCityGMLClass() == CityGMLClass.BUILDING) {
                        Building b = (Building) cityObject;
                        if (b.getMeasuredHeight() != null) {
                            list.add(createFeature(b));
                        }
                    }
                }
            }
        }
        reader.close();
    }

    private static Map<String, Object> createFeature(Building b) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("type", "Feature");
        Map<String, Object> geom = new HashMap<>();
        Map<String, Object> prop = new HashMap<>();
        ret.put("geometry", geom);
        ret.put("properties", prop);
        geom.put("type", "Polygon");
        MultiSurfaceProperty msp = b.getLod0FootPrint();
        // 関西は床データがあるのかな 東京は屋根データしかないです。
        if (msp == null)
            msp = b.getLod0RoofEdge();

        MultiSurface ms = msp.getMultiSurface();
        List<SurfaceProperty> spl = ms.getSurfaceMember();
        Polygon pp = (Polygon) spl.get(0).getGeometry();
        Exterior ex = (Exterior) pp.getExterior();
        LinearRing lr = (LinearRing) ex.getRing();
        DirectPositionList dpl = (DirectPositionList) lr.getPosList();
        List<Double> dl = dpl.toList3d();
        List<double[]> tmp = new ArrayList<>();
        double dem = 0.0;
        for (int i = 0; i < dl.size(); i = i + 3) {
            Double d01 = dl.get(i);
            Double d02 = dl.get(i + 1);
            Double d03 = dl.get(i + 2);
            tmp.add(new double[]{d02, d01});
            dem = d03;
        }
        List<List<double[]>> c = new ArrayList<>();
        c.add(tmp);

        geom.put("coordinates", c);
        prop.put("measuredHeight", b.getMeasuredHeight().getValue());
        List<AbstractGenericAttribute> ll = b.getGenericAttribute();
        for (AbstractGenericAttribute at : ll) {
            if (at instanceof StringAttribute) {
                StringAttribute st = (StringAttribute) at;
                prop.put(st.getName(), st.getValue());
            } else if (at instanceof MeasureAttribute) {
                MeasureAttribute st = (MeasureAttribute) at;
                prop.put(st.getName(), st.getValue().getValue());
            } else if (at instanceof IntAttribute) {
                IntAttribute st = (IntAttribute) at;
                prop.put(st.getName(), st.getValue());
            }
        }
        prop.put("dem", dem);
        return ret;
    }

    /**
     * スキーマURIすら安定しないというDXに泥臭く対応する
     * https://www.chisou.go.jp/tiiki/toshisaisei/itoshisaisei/iur/domain.pdf
     * @param f
     * @throws IOException
     */
    private static void updateSchema(File f) throws IOException {
        String uroSchemaOld = "http://www.kantei.go.jp/jp/singi/tiiki/toshisaisei/itoshisaisei/iur/uro/1.4";
        String uroSchemaNew = "https://www.chisou.go.jp/tiiki/toshisaisei/itoshisaisei/iur/uro/1.5";
        String uroXsdOld = "http://www.kantei.go.jp/jp/singi/tiiki/toshisaisei/itoshisaisei/iur/schemas/uro/1.4/urbanObject.xsd";
        String uroXsdNew = "https://www.chisou.go.jp/tiiki/toshisaisei/itoshisaisei/iur/schemas/uro/1.5/urbanObject.xsd";

        String tempFile = f.getParentFile() + "/" + f.getName() + ".tmp";
        BufferedReader in = new BufferedReader(new FileReader(f));
        BufferedWriter out = new BufferedWriter(new FileWriter(tempFile));

        String line;

        while ((line = in.readLine()) != null) {
            if (line.contains(uroSchemaOld))
                line = line.replaceAll(uroSchemaOld, uroSchemaNew);
            if (line.contains(uroXsdOld))
                line = line.replaceAll(uroXsdOld, uroXsdNew);

            out.write(line);
            out.newLine();
        }
        in.close();
        out.close();

        Files.move(new File(tempFile).toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void main(String[] args) {
        File in = new File(args[0]); //CityGMLのディレクトリ
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            for (File f : in.listFiles()) {
                if (f.isDirectory()) continue;
                if (f.getName().toLowerCase().endsWith(".gml")) {
                    System.out.println(f.getName());

                    updateSchema(f);

                    Map<String, Object> root = new HashMap<>();
                    root.put("type", "FeatureCollection");
                    List<Map<String, Object>> list = new ArrayList<>();
                    root.put("features", list);
                    readCityGML(f, list);
                    try {
                        File out = new File(f.getAbsolutePath().replace(".gml", ".geojson"));
                        BufferedWriter bw = new BufferedWriter(new FileWriter(out));
                        bw.write(gson.toJson(root));
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (CityGMLBuilderException e) {
            e.printStackTrace();
        } catch (CityGMLReadException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

