const fs = require('fs');
const path = require('path');

function copyFile(src, dest) {
	fs.mkdirSync(path.dirname(dest), { recursive: true });
	fs.copyFileSync(src, dest);
}

const root = path.join(__dirname, '..');
copyFile(
	path.join(root, 'nodes', 'Ispf', 'ispf.svg'),
	path.join(root, 'dist', 'nodes', 'Ispf', 'ispf.svg'),
);
copyFile(
	path.join(root, 'nodes', 'Ispf', 'Ispf.node.json'),
	path.join(root, 'dist', 'nodes', 'Ispf', 'Ispf.node.json'),
);
console.log('Copied node assets to dist/');
