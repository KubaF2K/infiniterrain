#version 330 core
in vec3 fragPosition;
in vec3 normal;

out vec4 fragColor;

uniform vec3 objectColor;
uniform vec3 lightColor;
uniform vec3 lightPosition;

void main() {
    float ambientStrength = 0.1f;
    vec3 ambient = ambientStrength * lightColor;

    vec3 normalized = normalize(normal);
    vec3 lightDirection = normalize(fragPosition - lightPosition);
    float diff = max(dot(normalized, lightDirection), 0.0f);
    vec3 diffuse = diff * lightColor;

    vec3 result = (ambient + diffuse) * objectColor;
    fragColor = vec4(result, 1.0f);
}