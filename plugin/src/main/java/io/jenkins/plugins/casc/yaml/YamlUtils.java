package io.jenkins.plugins.casc.yaml;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.Mapping;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import io.jenkins.plugins.casc.snakeyaml.composer.Composer;
import io.jenkins.plugins.casc.snakeyaml.nodes.MappingNode;
import io.jenkins.plugins.casc.snakeyaml.nodes.Node;
import io.jenkins.plugins.casc.snakeyaml.nodes.NodeId;
import io.jenkins.plugins.casc.snakeyaml.nodes.NodeTuple;
import io.jenkins.plugins.casc.snakeyaml.nodes.ScalarNode;
import io.jenkins.plugins.casc.snakeyaml.nodes.SequenceNode;
import io.jenkins.plugins.casc.snakeyaml.parser.ParserImpl;
import io.jenkins.plugins.casc.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Restricted(Beta.class)
public final class YamlUtils {

    public static final Logger LOGGER = Logger.getLogger(ConfigurationAsCode.class.getName());

    public static Node merge(List<YamlSource> configs) throws ConfiguratorException {
        Node root = null;
        for (YamlSource source : configs) {
            try (Reader r = source.read()) {

                final Node node = read(source);

                if (root == null) {
                    root = node;
                } else {
                    merge(root, node, source.toString());
                }
            } catch (IOException io) {
                throw new ConfiguratorException("Failed to read " + source, io);
            }
        }

        return root;
    }

    public static Node read(YamlSource source) throws IOException {
        Composer composer = new Composer(new ParserImpl(new StreamReaderWithSource(source)), new Resolver());
        return composer.getSingleNode();
    }

    private static void merge(Node root, Node node, String source) throws ConfiguratorException {
        if (root.getNodeId() != node.getNodeId()) {
            // means one of those yaml file doesn't conform to JCasC schema
            throw new ConfiguratorException(
                    String.format("Found incompatible configuration elements %s %s", source, node.getStartMark()));
        }

        switch (root.getNodeId()) {
            case sequence:
                SequenceNode seq = (SequenceNode) root;
                SequenceNode seq2 = (SequenceNode) node;
                seq.getValue().addAll(seq2.getValue());
                return;
            case mapping:
                MappingNode map = (MappingNode) root;
                MappingNode map2 = (MappingNode) node;
                // merge common entries
                final Iterator<NodeTuple> it = map2.getValue().iterator();
                while (it.hasNext()) {
                    NodeTuple t2 = it.next();
                    for (NodeTuple tuple : map.getValue()) {

                        final Node key = tuple.getKeyNode();
                        final Node key2 = t2.getKeyNode();
                        if (key.getNodeId() == NodeId.scalar) {
                            // We dont support merge for more complex cases (yet)
                            if (((ScalarNode) key).getValue().equals(((ScalarNode) key2).getValue())) {
                                merge(tuple.getValueNode(), t2.getValueNode(), source);
                                it.remove();
                            }
                        } else {
                            throw new ConfiguratorException(
                                    String.format("Found unmergeable configuration keys %s %s)", source, node.getEndMark()));
                        }
                    }
                }
                // .. and add others
                map.getValue().addAll(map2.getValue());
                return;
            default:
                throw new ConfiguratorException(
                        String.format("Found conflicting configuration at %s %s", source.toString(), node.getStartMark()));
        }

    }

    /**
     * Load configuration-as-code model from a set of Yaml sources, merging documents
     */
    public static Mapping loadFrom(List<YamlSource> sources) throws ConfiguratorException {
        if (sources.isEmpty()) return Mapping.EMPTY;
        final Node merged = merge(sources);
        if (merged == null) {
            LOGGER.warning("configuration-as-code yaml source returned an empty document.");
            return Mapping.EMPTY;
        }
        return loadFrom(merged);
    }

    /**
     * Load configuration-as-code model from a snakeyaml Node
     */
    private static Mapping loadFrom(Node node) {
        final ModelConstructor constructor = new ModelConstructor();
        constructor.setComposer(new Composer(null, null) {

            @Override
            public Node getSingleNode() {
                return node;
            }
        });
        return (Mapping) constructor.getSingleData(Mapping.class);
    }
}
