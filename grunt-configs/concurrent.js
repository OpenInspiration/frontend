module.exports = function () {
    return {
        options: {
            limit: 24,
            logConcurrentOutput: true
        },
        sass: ['sass:old-ie', 'sass:ie9', 'sass:modern', 'sass:inline']
    };
};
