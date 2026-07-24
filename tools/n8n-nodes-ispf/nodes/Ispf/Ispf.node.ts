import type {
	IDataObject,
	IExecuteFunctions,
	INodeExecutionData,
	INodeType,
	INodeTypeDescription,
	IRequestOptions,
} from 'n8n-workflow';
import { NodeOperationError } from 'n8n-workflow';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export class Ispf implements INodeType {
	description: INodeTypeDescription = {
		displayName: 'ISPF',
		name: 'ispf',
		icon: 'file:ispf.svg',
		group: ['transform'],
		version: 1,
		subtitle: '={{$parameter["resource"] + ": " + $parameter["operation"]}}',
		description: 'Call IoT Solutions Platform (ISPF) REST API',
		defaults: {
			name: 'ISPF',
		},
		inputs: ['main'],
		outputs: ['main'],
		credentials: [
			{
				name: 'ispfApi',
				required: true,
			},
		],
		requestDefaults: {
			baseURL: '={{$credentials.baseUrl}}',
			headers: {
				Accept: 'application/json',
			},
		},
		properties: [
			{
				displayName: 'Resource',
				name: 'resource',
				type: 'options',
				noDataExpression: true,
				options: [
					{ name: 'Object', value: 'object' },
					{ name: 'Variable', value: 'variable' },
					{ name: 'Event', value: 'event' },
					{ name: 'Function', value: 'function' },
					{ name: 'Workflow', value: 'workflow' },
					{ name: 'Platform', value: 'platform' },
				],
				default: 'variable',
			},

			// Object
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['object'] } },
				options: [
					{
						name: 'Get',
						value: 'get',
						action: 'Get object by path',
						description: 'GET /api/v1/objects/by-path',
					},
					{
						name: 'List Children',
						value: 'list',
						action: 'List child objects',
						description: 'GET /api/v1/objects?parent=',
					},
					{
						name: 'Get Editor',
						value: 'getEditor',
						action: 'Get object editor payload',
						description: 'GET /api/v1/objects/by-path/editor',
					},
				],
				default: 'get',
			},

			// Variable
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['variable'] } },
				options: [
					{
						name: 'Get',
						value: 'get',
						action: 'Get variable',
						description: 'GET /api/v1/objects/by-path/variables/detail',
					},
					{
						name: 'List',
						value: 'list',
						action: 'List variables',
						description: 'GET /api/v1/objects/by-path/variables',
					},
					{
						name: 'Set',
						value: 'set',
						action: 'Set variable value',
						description: 'PUT /api/v1/objects/by-path/variables',
					},
					{
						name: 'Get History',
						value: 'getHistory',
						action: 'Get variable history',
						description: 'GET /api/v1/objects/by-path/variables/history',
					},
				],
				default: 'get',
			},

			// Event
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['event'] } },
				options: [
					{
						name: 'Fire',
						value: 'fire',
						action: 'Fire event',
						description: 'POST /api/v1/events/fire',
					},
					{
						name: 'List Journal',
						value: 'list',
						action: 'List event journal',
						description: 'GET /api/v1/events',
					},
				],
				default: 'fire',
			},

			// Function
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['function'] } },
				options: [
					{
						name: 'Invoke',
						value: 'invoke',
						action: 'Invoke object function',
						description: 'POST /api/v1/objects/by-path/functions/invoke',
					},
					{
						name: 'BFF Invoke',
						value: 'bffInvoke',
						action: 'Invoke via BFF gateway',
						description: 'POST /api/v1/bff/invoke',
					},
				],
				default: 'invoke',
			},

			// Workflow
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['workflow'] } },
				options: [
					{
						name: 'Get',
						value: 'get',
						action: 'Get workflow',
						description: 'GET /api/v1/workflows/by-path',
					},
					{
						name: 'Run',
						value: 'run',
						action: 'Run workflow',
						description: 'POST /api/v1/workflows/by-path/run',
					},
				],
				default: 'run',
			},

			// Platform
			{
				displayName: 'Operation',
				name: 'operation',
				type: 'options',
				noDataExpression: true,
				displayOptions: { show: { resource: ['platform'] } },
				options: [
					{
						name: 'Get Info',
						value: 'info',
						action: 'Get platform info',
						description: 'GET /api/v1/info',
					},
					{
						name: 'Get Me',
						value: 'me',
						action: 'Get current principal',
						description: 'GET /api/v1/auth/me',
					},
				],
				default: 'info',
			},

			// Shared path fields
			{
				displayName: 'Object Path',
				name: 'objectPath',
				type: 'string',
				required: true,
				default: 'root.platform.devices.demo-sensor-01',
				displayOptions: {
					show: {
						resource: ['object'],
						operation: ['get', 'getEditor'],
					},
				},
				description: 'Object tree path, e.g. root.platform.devices.pump-01',
			},
			{
				displayName: 'Object Path',
				name: 'objectPath',
				type: 'string',
				required: true,
				default: 'root.platform.devices.demo-sensor-01',
				displayOptions: {
					show: {
						resource: ['variable', 'event', 'function'],
						operation: ['get', 'list', 'set', 'getHistory', 'fire', 'invoke'],
					},
				},
				description: 'Object tree path, e.g. root.platform.devices.pump-01',
			},
			{
				displayName: 'Parent Path',
				name: 'parentPath',
				type: 'string',
				required: true,
				default: 'root.platform.devices',
				displayOptions: {
					show: {
						resource: ['object'],
						operation: ['list'],
					},
				},
			},
			{
				displayName: 'Variable Name',
				name: 'variableName',
				type: 'string',
				required: true,
				default: 'temperature',
				displayOptions: {
					show: {
						resource: ['variable'],
						operation: ['get', 'set', 'getHistory'],
					},
				},
			},
			{
				displayName: 'Value (JSON)',
				name: 'variableValueJson',
				type: 'json',
				required: true,
				default: '{\n  "schema": {\n    "name": "temperature",\n    "fields": [{"name": "value", "type": "DOUBLE"}]\n  },\n  "rows": [{"value": 42.0}]\n}',
				displayOptions: {
					show: {
						resource: ['variable'],
						operation: ['set'],
					},
				},
				description: 'DataRecord body for PUT variable',
			},
			{
				displayName: 'History Limit',
				name: 'historyLimit',
				type: 'number',
				default: 100,
				displayOptions: {
					show: {
						resource: ['variable'],
						operation: ['getHistory'],
					},
				},
			},
			{
				displayName: 'Event Name',
				name: 'eventName',
				type: 'string',
				required: true,
				default: 'thresholdExceeded',
				displayOptions: {
					show: {
						resource: ['event'],
						operation: ['fire'],
					},
				},
			},
			{
				displayName: 'Payload (JSON)',
				name: 'eventPayloadJson',
				type: 'json',
				default: '{\n  "rows": [{}]\n}',
				displayOptions: {
					show: {
						resource: ['event'],
						operation: ['fire'],
					},
				},
			},
			{
				displayName: 'Journal Limit',
				name: 'journalLimit',
				type: 'number',
				default: 50,
				typeOptions: { maxValue: 200 },
				displayOptions: {
					show: {
						resource: ['event'],
						operation: ['list'],
					},
				},
			},
			{
				displayName: 'Function Name',
				name: 'functionName',
				type: 'string',
				required: true,
				default: 'acknowledgeAlarm',
				displayOptions: {
					show: {
						resource: ['function'],
						operation: ['invoke'],
					},
				},
			},
			{
				displayName: 'Arguments (JSON)',
				name: 'functionArgsJson',
				type: 'json',
				default: '{\n  "rows": [{}]\n}',
				displayOptions: {
					show: {
						resource: ['function'],
						operation: ['invoke'],
					},
				},
			},
			{
				displayName: 'BFF Body (JSON)',
				name: 'bffBodyJson',
				type: 'json',
				default: '{\n  "appId": "my-app",\n  "function": "myFn",\n  "input": {}\n}',
				displayOptions: {
					show: {
						resource: ['function'],
						operation: ['bffInvoke'],
					},
				},
			},
			{
				displayName: 'Workflow Path',
				name: 'workflowPath',
				type: 'string',
				required: true,
				default: 'root.platform.workflows.example',
				displayOptions: {
					show: {
						resource: ['workflow'],
					},
				},
			},
			{
				displayName: 'Trigger Object Path',
				name: 'triggerObjectPath',
				type: 'string',
				default: '',
				displayOptions: {
					show: {
						resource: ['workflow'],
						operation: ['run'],
					},
				},
			},
			{
				displayName: 'Input Map (JSON)',
				name: 'workflowInputJson',
				type: 'json',
				default: '{}',
				displayOptions: {
					show: {
						resource: ['workflow'],
						operation: ['run'],
					},
				},
				description: 'Maps to { "input": { ... } } on POST .../run',
			},
		],
	};

	async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
		const items = this.getInputData();
		const returnData: INodeExecutionData[] = [];

		for (let i = 0; i < items.length; i++) {
			try {
				const resource = this.getNodeParameter('resource', i) as string;
				const operation = this.getNodeParameter('operation', i) as string;
				const response = await dispatch.call(this, resource, operation, i);
				const executionData = this.helpers.constructExecutionMetaData(
					this.helpers.returnJsonArray(response as IDataObject),
					{ itemData: { item: i } },
				);
				returnData.push(...executionData);
			} catch (error) {
				if (this.continueOnFail()) {
					returnData.push({
						json: { error: (error as Error).message },
						pairedItem: { item: i },
					});
					continue;
				}
				throw error;
			}
		}

		return [returnData];
	}
}

async function dispatch(
	this: IExecuteFunctions,
	resource: string,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	switch (resource) {
		case 'object':
			return objectOps.call(this, operation, itemIndex);
		case 'variable':
			return variableOps.call(this, operation, itemIndex);
		case 'event':
			return eventOps.call(this, operation, itemIndex);
		case 'function':
			return functionOps.call(this, operation, itemIndex);
		case 'workflow':
			return workflowOps.call(this, operation, itemIndex);
		case 'platform':
			return platformOps.call(this, operation, itemIndex);
		default:
			throw new NodeOperationError(this.getNode(), `Unknown resource: ${resource}`, {
				itemIndex,
			});
	}
}

async function objectOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	if (operation === 'list') {
		const parentPath = this.getNodeParameter('parentPath', itemIndex) as string;
		return requestJson.call(this, 'GET', '/api/v1/objects', { parent: parentPath });
	}
	const objectPath = this.getNodeParameter('objectPath', itemIndex) as string;
	if (operation === 'get') {
		return requestJson.call(this, 'GET', '/api/v1/objects/by-path', { path: objectPath });
	}
	if (operation === 'getEditor') {
		return requestJson.call(this, 'GET', '/api/v1/objects/by-path/editor', { path: objectPath });
	}
	throw new NodeOperationError(this.getNode(), `Unknown object operation: ${operation}`, {
		itemIndex,
	});
}

async function variableOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	const objectPath = this.getNodeParameter('objectPath', itemIndex) as string;
	if (operation === 'list') {
		return requestJson.call(this, 'GET', '/api/v1/objects/by-path/variables', {
			path: objectPath,
		});
	}
	const variableName = this.getNodeParameter('variableName', itemIndex) as string;
	if (operation === 'get') {
		return requestJson.call(this, 'GET', '/api/v1/objects/by-path/variables/detail', {
			path: objectPath,
			name: variableName,
		});
	}
	if (operation === 'set') {
		const body = parseJsonParam(
			this,
			this.getNodeParameter('variableValueJson', itemIndex),
			itemIndex,
			'Value (JSON)',
		);
		return requestJson.call(
			this,
			'PUT',
			'/api/v1/objects/by-path/variables',
			{ path: objectPath, name: variableName },
			body,
		);
	}
	if (operation === 'getHistory') {
		const limit = this.getNodeParameter('historyLimit', itemIndex) as number;
		return requestJson.call(this, 'GET', '/api/v1/objects/by-path/variables/history', {
			path: objectPath,
			name: variableName,
			limit,
		});
	}
	throw new NodeOperationError(this.getNode(), `Unknown variable operation: ${operation}`, {
		itemIndex,
	});
}

async function eventOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	if (operation === 'list') {
		const objectPath = this.getNodeParameter('objectPath', itemIndex) as string;
		const limit = this.getNodeParameter('journalLimit', itemIndex) as number;
		return requestJson.call(this, 'GET', '/api/v1/events', {
			objectPath,
			limit,
		});
	}
	if (operation === 'fire') {
		const objectPath = this.getNodeParameter('objectPath', itemIndex) as string;
		const eventName = this.getNodeParameter('eventName', itemIndex) as string;
		const payload = parseJsonParam(
			this,
			this.getNodeParameter('eventPayloadJson', itemIndex),
			itemIndex,
			'Payload (JSON)',
		);
		return requestJson.call(this, 'POST', '/api/v1/events/fire', undefined, {
			objectPath,
			eventName,
			payload,
		});
	}
	throw new NodeOperationError(this.getNode(), `Unknown event operation: ${operation}`, {
		itemIndex,
	});
}

async function functionOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	if (operation === 'bffInvoke') {
		const body = parseJsonParam(
			this,
			this.getNodeParameter('bffBodyJson', itemIndex),
			itemIndex,
			'BFF Body (JSON)',
		);
		return requestJson.call(this, 'POST', '/api/v1/bff/invoke', undefined, body);
	}
	if (operation === 'invoke') {
		const objectPath = this.getNodeParameter('objectPath', itemIndex) as string;
		const functionName = this.getNodeParameter('functionName', itemIndex) as string;
		const body = parseJsonParam(
			this,
			this.getNodeParameter('functionArgsJson', itemIndex),
			itemIndex,
			'Arguments (JSON)',
		);
		return requestJson.call(
			this,
			'POST',
			'/api/v1/objects/by-path/functions/invoke',
			{ path: objectPath, name: functionName },
			body,
		);
	}
	throw new NodeOperationError(this.getNode(), `Unknown function operation: ${operation}`, {
		itemIndex,
	});
}

async function workflowOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	const workflowPath = this.getNodeParameter('workflowPath', itemIndex) as string;
	if (operation === 'get') {
		return requestJson.call(this, 'GET', '/api/v1/workflows/by-path', { path: workflowPath });
	}
	if (operation === 'run') {
		const triggerObjectPath = (
			this.getNodeParameter('triggerObjectPath', itemIndex) as string
		).trim();
		const input = parseJsonParam(
			this,
			this.getNodeParameter('workflowInputJson', itemIndex),
			itemIndex,
			'Input Map (JSON)',
		);
		const qs: IDataObject = { path: workflowPath };
		if (triggerObjectPath) {
			qs.triggerObjectPath = triggerObjectPath;
		}
		return requestJson.call(this, 'POST', '/api/v1/workflows/by-path/run', qs, { input });
	}
	throw new NodeOperationError(this.getNode(), `Unknown workflow operation: ${operation}`, {
		itemIndex,
	});
}

async function platformOps(
	this: IExecuteFunctions,
	operation: string,
	itemIndex: number,
): Promise<IDataObject | IDataObject[]> {
	if (operation === 'info') {
		return requestJson.call(this, 'GET', '/api/v1/info');
	}
	if (operation === 'me') {
		return requestJson.call(this, 'GET', '/api/v1/auth/me');
	}
	throw new NodeOperationError(this.getNode(), `Unknown platform operation: ${operation}`, {
		itemIndex,
	});
}

function parseJsonParam(
	ctx: IExecuteFunctions,
	raw: unknown,
	itemIndex: number,
	label: string,
): IDataObject {
	if (raw !== null && typeof raw === 'object' && !Array.isArray(raw)) {
		return raw as IDataObject;
	}
	if (typeof raw === 'string') {
		try {
			return JSON.parse(raw) as IDataObject;
		} catch {
			throw new NodeOperationError(ctx.getNode(), `${label} must be valid JSON`, {
				itemIndex,
			});
		}
	}
	throw new NodeOperationError(ctx.getNode(), `${label} must be a JSON object`, { itemIndex });
}

async function requestJson(
	this: IExecuteFunctions,
	method: HttpMethod,
	url: string,
	qs?: IDataObject,
	body?: IDataObject,
): Promise<IDataObject | IDataObject[]> {
	const credentials = await this.getCredentials('ispfApi');
	const baseUrl = String(credentials.baseUrl ?? '').replace(/\/+$/, '');
	const options: IRequestOptions = {
		method,
		baseURL: baseUrl,
		url,
		qs,
		body,
		json: true,
		timeout: 30000,
	};
	return (await this.helpers.requestWithAuthentication.call(
		this,
		'ispfApi',
		options,
	)) as IDataObject | IDataObject[];
}
