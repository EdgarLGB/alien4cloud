package org.alien4cloud.tosca.utils;

import java.util.Map;

import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.ConcatPropertyValue;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Test for function evaluation over a topology.
 */
public class FunctionEvaluatorTest {

    private void setPropertiesValues(Map<String, AbstractPropertyValue> properties, String prefix) {
        ScalarPropertyValue scalarPropValue = new ScalarPropertyValue(prefix + "scalar value");
        Map<String, Object> complex = Maps.newHashMap();
        complex.put("scalar", prefix + "complex scalar value");
        complex.put("map", Maps.newHashMap());
        ((Map) complex.get("map")).put("element_1", prefix + "element 1 value");
        ((Map) complex.get("map")).put("element_2", prefix + "element 2 value");
        complex.put("list", Lists.newArrayList(prefix + "list value 1", prefix + "list value 2"));
        ComplexPropertyValue complexPropValue = new ComplexPropertyValue(complex);

        FunctionPropertyValue getInputPropValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_INPUT, Lists.newArrayList("scalar_input"));
        FunctionPropertyValue getScalarPropValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY, Lists.newArrayList("SELF", "scalar_prop"));
        FunctionPropertyValue getComplexPropValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY,
                Lists.newArrayList("SELF", "complex_prop.scalar"));
        FunctionPropertyValue getComplexPropListValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY,
                Lists.newArrayList("SELF", "complex_prop.list[1]"));
        FunctionPropertyValue getComplexPropMapValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY,
                Lists.newArrayList("SELF", "complex_prop.map.element_1"));
        ConcatPropertyValue concatPropValue = new ConcatPropertyValue();
        concatPropValue.setFunction_concat("concat");
        concatPropValue.setParameters(
                Lists.newArrayList(new ScalarPropertyValue("input is: "), getInputPropValue, new ScalarPropertyValue(" property is: "), getScalarPropValue));
        FunctionPropertyValue getConcatPropValue = new FunctionPropertyValue(ToscaFunctionConstants.GET_PROPERTY, Lists.newArrayList("SELF", "concat_prop"));

        ConcatPropertyValue concatGetConcatPropValue = new ConcatPropertyValue();
        concatGetConcatPropValue.setFunction_concat("concat");
        concatGetConcatPropValue.setParameters(Lists.newArrayList(new ScalarPropertyValue("get concat is: "), getConcatPropValue));

        properties.put("scalar_prop", scalarPropValue);
        properties.put("complex_prop", complexPropValue);
        properties.put("get_input_prop", getInputPropValue);
        properties.put("get_scalar_prop", getScalarPropValue);
        properties.put("get_complex_prop", getComplexPropValue);
        properties.put("get_complex_prop_list", getComplexPropListValue);
        properties.put("get_complex_prop_map", getComplexPropMapValue);
        properties.put("concat_prop", concatPropValue);
        properties.put("get_concat_prop", getConcatPropValue);
        properties.put("concat_get_concat_prop", concatGetConcatPropValue);
    }

    /**
     * Generate the topology below (no need type validations) and inputs to be used for next tests.
     * 
     * @return A function evaluator context for the test topology.
     */
    private FunctionEvaluatorContext getEvaluationContext() {
        NodeTemplate myNode = new NodeTemplate();
        myNode.setProperties(Maps.newHashMap());
        setPropertiesValues(myNode.getProperties(), "");

        Capability capability = new Capability();
        myNode.setCapabilities(Maps.newHashMap());
        myNode.getCapabilities().put("my_capability", capability);
        capability.setProperties(Maps.newHashMap());
        setPropertiesValues(capability.getProperties(), "capa ");

        Topology topology = new Topology();
        topology.setNodeTemplates(Maps.newHashMap());
        topology.getNodeTemplates().put("my_node", myNode);

        Map<String, PropertyValue> inputs = Maps.newHashMap();
        inputs.put("scalar_input", new ScalarPropertyValue("scalar input value"));

        return new FunctionEvaluatorContext(topology, inputs);
    }

    @Test
    public void evaluateNullProperty() {
        Assert.assertNull(FunctionEvaluator.resolveValue(null, null, null, null));
    }

    @Test
    public void nodeGetInputProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(), template.getProperties().get("get_input_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("scalar input value", resolved.getValue());
    }

    @Test
    public void nodeGetScalarProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(), template.getProperties().get("get_scalar_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("scalar value", resolved.getValue());
    }

    @Test
    public void nodeGetComplexProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(), template.getProperties().get("get_complex_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("complex scalar value", resolved.getValue());
    }

    @Test
    public void nodeGetComplexPropList() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(),
                template.getProperties().get("get_complex_prop_list"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("list value 2", resolved.getValue());
    }

    @Test
    public void nodeGetComplexPropMap() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(),
                template.getProperties().get("get_complex_prop_map"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("element 1 value", resolved.getValue());
    }

    @Test
    public void nodeConcatProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(), template.getProperties().get("concat_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("input is: scalar input value property is: scalar value", resolved.getValue());
    }

    @Test
    public void nodeGetConcatProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(), template.getProperties().get("get_concat_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("input is: scalar input value property is: scalar value", resolved.getValue());
    }

    @Test
    public void nodeConcatGetConcatProp() {
        FunctionEvaluatorContext context = getEvaluationContext();
        NodeTemplate template = context.getTopology().getNodeTemplates().get("my_node");

        PropertyValue resolved = FunctionEvaluator.resolveValue(context, template, template.getProperties(),
                template.getProperties().get("concat_get_concat_prop"));
        Assert.assertNotNull(resolved);
        Assert.assertEquals(ScalarPropertyValue.class, resolved.getClass());
        Assert.assertEquals("get concat is: input is: scalar input value property is: scalar value", resolved.getValue());
    }
}