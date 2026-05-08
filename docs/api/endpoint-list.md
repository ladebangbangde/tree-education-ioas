# Endpoint List

All endpoints are under `/api/v1` and use `Authorization: Bearer <token>` except `POST /auth/login`.

## Auth
- `POST /auth/login`
- `GET /auth/me`

## Operators
- `GET /operators/options`
- `GET /operators?name=&pageNum=&pageSize=`

## Media Content Packages
- `GET /media/content/packages?pageNum=&pageSize=&keyword=&operatorId=&status=&tab=mine|draft|record|recycle`
- `POST /media/content/packages`
- `GET /media/content/packages/{id}`
- `PUT /media/content/packages/{id}`
- `DELETE /media/content/packages/{id}`
- `POST /media/content/packages/{id}/files` with multipart fields `scripts`, `videos`, `images`

## Media Assets and Recycle Bin
- `GET /media/assets?packageId=&fileType=all|script|video|image&pageNum=&pageSize=`
- `GET /media/assets/{id}`
- `DELETE /media/assets/{id}`
- `GET /media/assets/{id}/download`
- `GET /media/assets/{id}/preview`
- `GET /media/assets/recycle-bin?keyword=&fileType=&deletedBy=&packageId=&operatorId=&pageNum=&pageSize=`
- `POST /media/assets/recycle-bin/{id}/restore`
- `DELETE /media/assets/recycle-bin/{id}/purge`

## Resource Center
- `GET /media/resources/tree`
- `GET /media/resources/packages?keyword=&operatorId=&pageNum=&pageSize=`

## Leads
- `GET /leads?tab=unassigned|assigned|mine&keyword=&relatedPackageId=&operatorId=&pageNum=&pageSize=`
- `POST /leads`
- `GET /leads/{id}`
- `PATCH /leads/{id}/status`
- `PATCH /leads/{id}`

## Tasks
- `GET /tasks/media`
- `GET /tasks/operator`
- `PATCH /tasks/{id}`

## Reports
- `GET /reports/media-output`
- `GET /reports/operator-leads`
- `GET /reports/operator/by-package`
- `GET /reports/operator/trend`

## Audit
- `GET /audit/logs`
