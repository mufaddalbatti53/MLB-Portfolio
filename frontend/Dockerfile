FROM python:3.13-alpine AS site-builder
WORKDIR /source
COPY frontend ./frontend
COPY scripts ./scripts
ARG SITE_BASE_URL
ARG SITE_DISPLAY_NAME
ARG SITE_EMAIL
ARG SITE_LOCATION
ARG SITE_GITHUB
ARG SITE_LINKEDIN
ARG BUILD_MODE=production
ENV SITE_BASE_URL=${SITE_BASE_URL} \
    SITE_DISPLAY_NAME=${SITE_DISPLAY_NAME} \
    SITE_EMAIL=${SITE_EMAIL} \
    SITE_LOCATION=${SITE_LOCATION} \
    SITE_GITHUB=${SITE_GITHUB} \
    SITE_LINKEDIN=${SITE_LINKEDIN}
RUN python scripts/build_site.py --mode "${BUILD_MODE}" --output /site

FROM nginx:1.28-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=site-builder /site/ /usr/share/nginx/html/
EXPOSE 8080
