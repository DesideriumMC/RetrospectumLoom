package net.fabricmc.loom;

import org.junit.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import static org.junit.Assert.*;

public class RunConfigTest {
    @Test public void ideaTemplateEscapesArgumentsAndUsesConfiguredDirectory() throws Exception {
        Project project = ProjectBuilder.builder().withName("example").build();
        RunConfig config = RunConfig.defaultServerRunConfig(project);
        config.setName("Server & Tools");
        config.setRunDir("server with spaces");
        config.vmArg("-Dexample=a b&c");
        String xml = config.configureTemplate("idea_run_config_template.xml");
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("Server & Tools", ((org.w3c.dom.Element) document.getElementsByTagName("configuration").item(0)).getAttribute("name"));
        boolean directory = false, argument = false;
        org.w3c.dom.NodeList options = document.getElementsByTagName("option");
        for (int i = 0; i < options.getLength(); i++) {
            org.w3c.dom.Element option = (org.w3c.dom.Element) options.item(i);
            if (option.getAttribute("name").equals("WORKING_DIRECTORY")) directory = option.getAttribute("value").equals(config.resolveRunDir().toAbsolutePath().toString());
            if (option.getAttribute("name").equals("VM_PARAMETERS")) argument = option.getAttribute("value").equals("\"-Dexample=a b&c\"");
        }
        assertTrue(directory); assertTrue(argument);
    }
    @Test public void namespacedPomDoesNotGainDuplicateDependencyNodes() throws Exception {
        groovy.util.Node root = new groovy.xml.XmlParser().parseText("<project xmlns='http://maven.apache.org/POM/4.0.0'><dependencies><dependency><groupId>example</groupId></dependency></dependencies></project>");
        groovy.util.Node dependencies = net.fabricmc.loom.util.GroovyXmlUtil.getOrCreateNode(root, "dependencies");
        assertEquals(1, root.children().size());
        assertTrue(net.fabricmc.loom.util.GroovyXmlUtil.getNode(dependencies, "dependency").isPresent());
    }
}
