// Sample JavaScript resource
function greet(name) {
    return `Hello, ${name}!`;
}

const config = {
    apiEndpoint: '/mcp',
    timeout: 5000,
    debug: true
};

export { greet, config };
