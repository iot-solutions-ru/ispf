package com.ispf.server.plugin.oilterminal;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.oilterminal.DispatchStatus;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Seeds oil terminal reference stand objects (P-301 demo tree).
 */
@Component
public class OilTerminalApplicationRunner {

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;
    private final ObjectManager objectManager;

    public OilTerminalApplicationRunner(
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ObjectManager objectManager
    ) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.objectManager = objectManager;
    }

    public void ensureReferenceStand() {
        if (objectManager.tree().findByPath(OilTerminalConstants.ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "oil-terminal",
                    ObjectType.CUSTOM,
                    "Oil Terminal",
                    "Reference MES stand — tanks, racks, dispatch orders",
                    null
            );
            createChildContainer("tanks", "Tanks", "Storage tanks (РВС)");
            createChildContainer("racks", "Racks", "Loading racks");
            createChildContainer("orders", "Orders", "Dispatch orders");
            createChildContainer("samples", "Samples", "Lab samples");
            createChildContainer("workflows", "Workflows", "Oil terminal BPMN");
            createChildContainer("dashboards", "Dashboards", "Operator screens");

            instantiateTank("rvs1", "DT");
            instantiateTank("rvs2", "DT");
            instantiateTank(OilTerminalConstants.DEMO_TANK, "DT");
            instantiateRack("rack1");
            instantiateRack(OilTerminalConstants.DEMO_RACK);

            PlatformObject order = instantiateOrder(OilTerminalConstants.DEMO_ORDER);
            configureDemoOrder(order.path());

            instantiateSample("sample-rvs3-01", OilTerminalConstants.DEMO_TANK);
            ensureWorkflows();
        }
        ensureDashboards();
    }

    private void createChildContainer(String name, String displayName, String description) {
        objectManager.create(
                OilTerminalConstants.ROOT,
                name,
                ObjectType.CUSTOM,
                displayName,
                description,
                null
        );
    }

    private void instantiateTank(String name, String productCode) {
        modelRegistry.findByName(OilTerminalConstants.MODEL_TANK).ifPresent(model -> {
            modelEngine.instantiateModel(model.id(), OilTerminalConstants.TANKS, name, Map.of());
            String path = OilTerminalConstants.tankPath(name);
            OilTerminalObjects.setString(objectManager, path, "productCode", productCode);
            objectManager.persistNodeTree(path);
        });
    }

    private void instantiateRack(String name) {
        modelRegistry.findByName(OilTerminalConstants.MODEL_RACK).ifPresent(model -> {
            modelEngine.instantiateModel(model.id(), OilTerminalConstants.RACKS, name, Map.of());
            objectManager.persistNodeTree(OilTerminalConstants.rackPath(name));
        });
    }

    private PlatformObject instantiateOrder(String name) {
        return modelRegistry.findByName(OilTerminalConstants.MODEL_DISPATCH).map(model -> {
            PlatformObject order = modelEngine.instantiateModel(model.id(), OilTerminalConstants.ORDERS, name, Map.of());
            objectManager.persistNodeTree(order.path());
            return order;
        }).orElseThrow(() -> new IllegalStateException("Dispatch model missing"));
    }

    private void configureDemoOrder(String orderPath) {
        OilTerminalObjects.setString(objectManager, orderPath, "orderNo", "4521");
        OilTerminalObjects.setString(objectManager, orderPath, "productCode", "DT");
        OilTerminalObjects.setDouble(objectManager, orderPath, "plannedLiters", 20000.0);
        OilTerminalObjects.setString(objectManager, orderPath, "vehiclePlate", "A123BC77");
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.PLANNED.wireValue());
        objectManager.persistNodeTree(orderPath);
    }

    private void instantiateSample(String name, String tankName) {
        modelRegistry.findByName(OilTerminalConstants.MODEL_SAMPLE).ifPresent(model -> {
            modelEngine.instantiateModel(model.id(), OilTerminalConstants.SAMPLES, name, Map.of());
            String path = OilTerminalConstants.samplePath(name);
            OilTerminalObjects.setString(objectManager, path, "tankName", tankName);
            OilTerminalObjects.setString(objectManager, path, "sampleNo", "S-2026-001");
            objectManager.persistNodeTree(path);
        });
    }

    private void ensureWorkflows() {
        modelRegistry.findByName("workflow-v1").ifPresent(model -> {
            createWorkflow(
                    "dispatch-truck",
                    "Dispatch Truck (P-301)",
                    OilTerminalWorkflowDefinitions.DISPATCH_TRUCK,
                    "{}"
            );
            createWorkflow(
                    "lab-approval",
                    "Lab Approval (P-210)",
                    OilTerminalWorkflowDefinitions.LAB_APPROVAL,
                    "{}"
            );
        });
    }

    private void createWorkflow(String name, String title, String bpmnXml, String triggerJson) {
        String path = OilTerminalConstants.WORKFLOWS + "." + name;
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        objectManager.create(
                OilTerminalConstants.WORKFLOWS,
                name,
                ObjectType.WORKFLOW,
                title,
                "Oil terminal reference workflow",
                "workflow-v1"
        );
        modelEngine.applyModel(
                modelRegistry.requireByName("workflow-v1").id(),
                path
        );
        PlatformObject workflow = objectManager.require(path);
        workflow.setVariableValue("title", stringRecord(title));
        workflow.setVariableValue("status", stringRecord(WorkflowLifecycleStatus.ACTIVE.name()));
        workflow.setVariableValue("bpmnXml", stringRecord(bpmnXml.trim()));
        workflow.setVariableValue("triggerJson", stringRecord(triggerJson));
        objectManager.persistNodeTree(path);
    }

    private void ensureDashboards() {
        modelRegistry.findByName("dashboard-v1").ifPresent(model -> {
            upsertDashboard(
                    "dispatcher",
                    "Oil Terminal — Dispatcher",
                    OilTerminalDashboardLayouts.DISPATCHER
            );
            upsertDashboard(
                    "rack-operator",
                    "Oil Terminal — Rack Operator",
                    OilTerminalDashboardLayouts.RACK_OPERATOR
            );
            upsertDashboard(
                    "lab-operator",
                    "Oil Terminal — Lab Operator",
                    OilTerminalDashboardLayouts.LAB_OPERATOR
            );
        });
    }

    private void upsertDashboard(String name, String title, String layoutJson) {
        String path = OilTerminalConstants.DASHBOARDS + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    OilTerminalConstants.DASHBOARDS,
                    name,
                    ObjectType.DASHBOARD,
                    title,
                    "Oil terminal HMI",
                    "dashboard-v1"
            );
            modelEngine.applyModel(modelRegistry.requireByName("dashboard-v1").id(), path);
        }
        PlatformObject dashboard = objectManager.require(path);
        dashboard.setVariableValue("title", stringRecord(title));
        dashboard.setVariableValue("layout", stringRecord(layoutJson.trim()));
        objectManager.persistNodeTree(path);
    }

    private static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }
}
