{{- define "kross.labels" -}}
app.kubernetes.io/name: kross
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "kross.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end }}

{{- define "kross.image" -}}
{{- $registry := default "" .root.Values.images.registry | trimSuffix "/" -}}
{{- if $registry -}}
{{- printf "%s/%s" $registry .image -}}
{{- else -}}
{{- .image -}}
{{- end -}}
{{- end }}

{{- define "kross.imagePullSecrets" -}}
{{- with .Values.images.pullSecrets }}
imagePullSecrets:
{{- range . }}
  - name: {{ . | quote }}
{{- end }}
{{- end }}
{{- end }}

{{- define "kross.imagePullSecretsCsv" -}}
{{- join "," (.Values.images.pullSecrets | default list) -}}
{{- end }}

{{- define "kross.s3PublicEndpoint" -}}
{{- $explicit := default "" .Values.app.s3PublicEndpoint | trim -}}
{{- if $explicit -}}
{{- $explicit -}}
{{- else if and .Values.rustfs.ingress.enabled .Values.rustfs.ingress.host -}}
{{- printf "%s://%s" (ternary "https" "http" .Values.rustfs.ingress.tls) .Values.rustfs.ingress.host -}}
{{- else -}}
http://localhost:9000
{{- end -}}
{{- end }}
