import type {
	IAuthenticateGeneric,
	ICredentialTestRequest,
	ICredentialType,
	INodeProperties,
} from 'n8n-workflow';

export class IspfApi implements ICredentialType {
	name = 'ispfApi';

	displayName = 'ISPF API';

	documentationUrl = 'https://github.com/IoT-Solutions-Platform/IoT-Solutions-Platform';

	properties: INodeProperties[] = [
		{
			displayName: 'Base URL',
			name: 'baseUrl',
			type: 'string',
			required: true,
			default: 'http://localhost:8080',
			placeholder: 'http://localhost:8080',
			description:
				'ISPF server base URL without trailing slash. Do not include /api/v1.',
		},
		{
			displayName: 'Access Token',
			name: 'accessToken',
			type: 'string',
			typeOptions: { password: true },
			required: true,
			default: '',
			description:
				'Bearer token from POST /api/v1/auth/login (response field "token"), or a JWT from your IdP.',
		},
	];

	authenticate: IAuthenticateGeneric = {
		type: 'generic',
		properties: {
			headers: {
				Authorization: '=Bearer {{$credentials.accessToken}}',
			},
		},
	};

	test: ICredentialTestRequest = {
		request: {
			baseURL: '={{$credentials.baseUrl}}',
			url: '/api/v1/auth/me',
		},
	};
}
