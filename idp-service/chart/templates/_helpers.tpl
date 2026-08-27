{{/*
Expand the name of the chart.
*/}}
{{- define "idp-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "idp-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "idp-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "idp-service.labels" -}}
helm.sh/chart: {{ include "idp-service.chart" . }}
{{ include "idp-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "idp-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "idp-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "idp-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "idp-service.name" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{/*
Create the name of the security image to use
*/}}
{{- define "idp-service.securityImage" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.securityImage.image "global" .Values.global) }}
{{- end }}


{{- define "idp-service.pullSecrets" -}}
{{- if .Values.waitForInitContainer }}
    {{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- else }}
    {{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.global.waitForInitContainer.image) "global" .Values.global ) }}
{{- end }}
{{- end -}}


{{/*
IDP Service Secrets

USAGE:
{{- "idp-service.generateSecrets" (dict "ctx" $)}}
*/}}
{{- define "idp-service.generateSecrets" }}
    {{- $ := .ctx }}
    {{- $hasAtleastOneSecret := false }}
    {{- $localESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ )) }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "BACKSTAGE_CA_CERT")) "true" }}
    {{- $hasAtleastOneSecret = true }}
BACKSTAGE_CA_CERT: {{ .ctx.Values.secrets.default.BACKSTAGE_CA_CERT_SECRET | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "BACKSTAGE_SA_TOKEN")) "true" }}
    {{- $hasAtleastOneSecret = true }}
BACKSTAGE_SA_TOKEN: {{ .ctx.Values.secrets.default.BACKSTAGE_SA_TOKEN_SECRET | b64enc }}
    {{- end }}
    {{- if not $hasAtleastOneSecret }}
{}
    {{- end }}
{{- end }}