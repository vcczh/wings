package edu.isi.wings.portal.controllers;

import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.util.Scanner;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import edu.isi.wings.catalog.component.ComponentFactory;
import edu.isi.wings.catalog.component.api.ComponentCreationAPI;
import edu.isi.wings.catalog.component.classes.Component;
import edu.isi.wings.catalog.component.classes.ComponentRole;
import edu.isi.wings.catalog.data.DataFactory;
import edu.isi.wings.catalog.data.api.DataCreationAPI;
import edu.isi.wings.catalog.data.classes.DataItem;
import edu.isi.wings.portal.classes.Config;
import edu.isi.wings.portal.classes.JsonHandler;
//import edu.isi.wings.classes.kb.PropertiesHelper;
import edu.isi.wings.portal.classes.html.CSSLoader;
import edu.isi.wings.portal.classes.html.JSLoader;
import edu.isi.wings.workflow.template.TemplateFactory;
import edu.isi.wings.workflow.template.api.Template;
import edu.isi.wings.workflow.template.api.TemplateCreationAPI;
import edu.isi.wings.workflow.template.classes.Link;
import edu.isi.wings.workflow.template.classes.Role;
import edu.isi.wings.workflow.template.classes.sets.Binding;
import edu.isi.wings.workflow.template.classes.variables.ComponentVariable;
import edu.isi.wings.workflow.template.classes.variables.Variable;
import edu.isi.wings.workflow.template.classes.variables.VariableType;

public class TemplateController {
	private int guid;

	private DataCreationAPI dc;
	private ComponentCreationAPI cc;
	private TemplateCreationAPI tc;

	private Config config;

	private Gson json;
	private Properties props;

	private String wliburl;
	private String dcdomns;
	private String dclibns;
	private String pcdomns;
	private String wflowns;
	
	private String planScript;
	private String runScript;
	private String thisScript;
	
	public TemplateController(int guid, Config config) {
		this.guid = guid;
		this.config = config;
		this.json = JsonHandler.createTemplateGson();
		this.props = config.getProperties();

		tc = TemplateFactory.getCreationAPI(props);
		cc = ComponentFactory.getCreationAPI(props, true);
		dc = DataFactory.getCreationAPI(props);

		this.wliburl = (String) props.get("domain.workflows.dir.url");
		this.dcdomns = (String) props.get("ont.domain.data.url") + "#";
		this.dclibns = (String) props.get("lib.domain.data.url") + "#";
		this.pcdomns = (String) props.get("ont.domain.component.ns");
		this.wflowns = (String) props.get("ont.workflow.url") + "#";
		
		this.planScript = config.getContextRootPath() + "/plan";
		this.runScript = config.getContextRootPath() + "/run";
		this.thisScript = config.getScriptPath();
	}

	public void show(PrintWriter out, HashMap<String, Boolean> options, String tid, boolean editor, boolean tellme) {
		try {
			// Get Hierarchy
			String tree = json.toJson(tc.getTemplateList());
			String optionstr = json.toJson(options);
			
			out.println("<html>");
			out.println("<head>");
			out.println("<title>Template "+(editor ? "Editor" : "Browser")+"</title>");
			JSLoader.setContextInformation(out, config);
			CSSLoader.loadTemplateViewer(out, config.getContextRootPath());
			JSLoader.loadTemplateViewer(out, config.getContextRootPath(), tellme);
			out.println("</head>");
	
			out.println("<script>");
			out.println("var opts = "+optionstr+";");
			out.println("var tBrowser_" + guid + ";");
			
			// FIXME: Propvals issue: 
			// - Add propval values -- get values via an ajax call ?
			out.println("Ext.onReady(function() {"
					+ "Ext.QuickTips.init();\n"
					+ "tBrowser_" + guid + " = new TemplateBrowser('" + guid + "', opts, { "
							+ "tree: " + tree
							+ ", components: { tree: " + json.toJson(cc.getComponentHierarchy(editor).getRoot()) + "}"
							+ (editor ? ", propvals: "+ json.toJson(this.getConstraintProperties()) : "") 
							+ (editor ? ", data: { tree: " + json.toJson(dc.getDatatypeHierarchy().getRoot()) + "}" : "")
//							+ (tellme ? ", beamer_paraphrases: " + this.getBeamerParaphrasesJSON() : "")
//							+ (tellme ? ", beamer_mappings: " + this.getBeamerMappingsJSON() : "")
					+ " }, "
					+ editor + ", "
					+ tellme + ", "
					+ "'" + this.thisScript + "', "
					+ "'" + this.planScript + "', "
					+ "'" + this.runScript + "', "
					+ "'" + this.runScript + "', "
					+ "'" + this.wliburl + "', "
					+ "'" + this.dcdomns + "', "
					+ "'" + this.dclibns + "', "
					+ "'" + this.pcdomns + "', "
					+ "'" + this.wflowns + "'"
					+ ");\n"
					+ "tBrowser_" + guid + ".initialize(" 
					+ (tid != null ? "'"+tid+"'" : "")
					+ ");\n"
					+ "});");
			
			out.println("</script>");
	
			out.println("</html>");
		}
		finally {
			dc.end();
			cc.end();
			tc.end();
		}
	}
	
	public String getViewerJSON(String tplid) {
		Template tpl = null;
		try {
			tpl = this.tc.getTemplate(tplid);
			HashMap<String, Object> extra = new HashMap<String, Object>();
			extra.put("inputs",  this.getTemplateInputs(tpl));
			return JsonHandler.getTemplateJSON(json, tpl, extra);
		}
		finally {
			if(tpl != null)
				tpl.end();
			dc.end();
			cc.end();
			tc.end();
		}
	}
	
	public String getEditorJSON(String tplid) {
		Template tpl = null;
		try {
			tpl = this.tc.getTemplate(tplid);
			return JsonHandler.getTemplateJSON(json, tpl, null);
		}
		finally {
			if(tpl != null)
				tpl.end();
			dc.end();
			cc.end();
			tc.end();
		}
	}

	public synchronized String saveTemplateJSON(String tplid, String templatejson, String consjson) {
		Template tpl = null;
		try {
			tpl = JsonHandler.getTemplateFromJSON(this.json, templatejson, consjson);
			
			if(!tpl.getMetadata().getContributors().contains(this.config.getUserId()))
				tpl.getMetadata().addContributor(this.config.getUserId());
			
			if(tpl != null) {
				boolean ok = false;
				if(tplid.equals(tpl.getID()))
					ok = this.tc.saveTemplate(tpl);
				else
					ok = this.tc.saveTemplateAs(tpl, tplid);
				if(ok) 
					return "OK";
			}
			return "";
		}
		finally {
			if(tpl != null)
				tpl.end();
			dc.end();
			cc.end();
			tc.end();
		}
	}
	
	public synchronized String deleteTemplate(String tplid) {
		Template tpl = null;
		try {
			tpl = this.tc.getTemplate(tplid);
			if(tpl != null) {
				if(this.tc.removeTemplate(tpl))
					return "OK";
			}
			return "";
		}
		finally {
			if(tpl != null)
				tpl.end();
			dc.end();
			cc.end();
			tc.end();
		}
	}
	
	public synchronized String newTemplate(String tplid) {
		Template tpl = null;
		try {
			tpl = this.tc.createTemplate(tplid);
			if(tpl != null) {
				if(this.tc.saveTemplate(tpl))
					return "OK";
			}
			return "";
		}
		finally {
			if(tpl != null)
				tpl.end();
			dc.end();
			cc.end();
			tc.end();
		}
	}
	
	public String getDotLayout(String dotstr) throws IOException {
		HashMap<String, Float[]> idpositions = new HashMap<String, Float[]>();

		// Run the dot executable
		Process dot = Runtime.getRuntime().exec(this.config.getDotFile());
		
		// Write dot-format graph to dot
		PrintWriter bout = new PrintWriter(dot.getOutputStream());
		bout.println(dotstr);
		bout.flush();
		
		// Read position annotated graph from dot
		BufferedReader bin = new BufferedReader(new InputStreamReader(dot.getInputStream()));
		Pattern pospattern = Pattern.compile("^\\s*(.+?)\\s.+pos=\"([\\d\\.]+),([\\d\\.]+)\"");
		
		String curline = "", line;
		while((line = bin.readLine()) != null) {
			line = line.trim();
			if(line.equals("}"))
				break;			
			if(line.endsWith("\\")) 
				line = line.substring(0, line.length()-1);
			
			if(!line.endsWith(";")) {
				curline += " " + line;
				continue;
			}
			curline += line;
			curline = curline.trim();
			Matcher m = pospattern.matcher(curline);
			if(m.find()) {
				idpositions.put(
						m.group(1), 
						new Float[]{ Float.parseFloat(m.group(2)), Float.parseFloat(m.group(3)) }
				);
			}
			curline = "";
		}
		bin.close();
		
		float maxY = 0;
		for(String id : idpositions.keySet()) {
			Float[] pos = idpositions.get(id);
			if(maxY < pos[1])
				maxY = pos[1];
		}
		
		String retval = "";
		for(String id : idpositions.keySet()) {
			Float[] pos = idpositions.get(id);
			retval += id+":"+pos[0]+","+(40+maxY-pos[1])+"\n";
		}
		return retval;
	}
	
//	private String getBeamerParaphrasesJSON() {
//		return this.getJSONFileContents(this.config.getArchivedDomainDir()+"/beamer/paraphrases.json");
//	}
//	
//	private String getBeamerMappingsJSON() {
//		return this.getJSONFileContents(this.config.getArchivedDomainDir()+"/beamer/mappings.json");
//	}
//	
//	private String getJSONFileContents(String path) {
//		String str = "";
//		try {
//			Scanner sc = new Scanner(new File(path));
//			while (sc.hasNextLine()) {
//				str += sc.nextLine() + "\n";
//			}
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//			return "{}";
//		}
//		return str;
//	}
	
	private ArrayList<Object> getTemplateInputs(Template tpl) {
		HashMap<String, Integer> varDims = new HashMap<String, Integer>();
		HashMap<String, Role> iroles = tpl.getInputRoles();
		for (String varid : iroles.keySet()) {
			Role r = iroles.get(varid);
			varDims.put(varid, r.getDimensionality());
		}

		ArrayList<Object> returnList = new ArrayList<Object>();

		// Some caches
		HashMap<String, Boolean> varsDone = new HashMap<String, Boolean>();
		HashMap<String, Component> compCache = new HashMap<String, Component>();

		// Go through the input links
		for (Link l : tpl.getInputLinks()) {
			Variable var = l.getVariable();

			if(var.isAutoFill())
			  continue;
			if (varsDone.containsKey(var.getID()))
				continue;
			varsDone.put(var.getID(), true);

			ComponentVariable cvar = l.getDestinationNode().getComponentVariable();
			Binding cbinding = cvar.getBinding();
			String cid = cbinding.getID();

			Binding varbinding = var.getBinding();

			// Fetch component details from PC
			Component comp = compCache.containsKey(cid) ? compCache.get(cid) : this.cc
					.getComponent(cid, true);

			// Can't find this component in the catalog ? Skip
			if(comp == null) continue;
			
			// Get relevant role details
			String rolename = l.getDestinationPort().getRole().getRoleId();
			String roletypeid = null;
			int roledim = 0;
			for (ComponentRole crole : comp.getInputs()) {
				if (crole.getType() == null)
					continue;
				if (crole.getRoleName().equals(rolename)) {
					roletypeid = crole.getType();
					roledim = crole.getDimensionality();
				}
			}

			// Variable details
			HashMap<String, Object> vardata = new HashMap<String, Object>();
			vardata.put("id", var.getID());
			vardata.put("name", var.getName());
			if (var.getVariableType() == VariableType.DATA) {
				vardata.put("type", "data");
				if (roletypeid != null) {
					ArrayList<DataItem> dataitems = this.dc.getDataForDatatype(roletypeid, false);
					ArrayList<String> items = new ArrayList<String>();
					for (DataItem ditem : dataitems) {
						items.add(ditem.getID());
					}
					vardata.put("options", items);
				}
				int dim = varDims.containsKey(var.getID()) ? varDims.get(var.getID()) : roledim;
				vardata.put("dim", dim);

				if (varbinding != null) {
					vardata.put("binding", varbinding.getID());
				}
			} else if (var.getVariableType() == VariableType.PARAM) {
				vardata.put("type", "param");
				vardata.put("dtype", roletypeid);
				vardata.put("binding", (varbinding != null ? varbinding.getValue() : ""));
			}
			returnList.add(vardata);
		}
		return returnList;
	}
	
	private ArrayList<Object> getConstraintProperties() {
		ArrayList<Object> allprops = new ArrayList<Object>();
		allprops.addAll(dc.getAllMetadataProperties());
		allprops.addAll(tc.getAllConstraintProperties());
		return allprops;
	}
}