# - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - #
FROM node:21.7.2-slim AS frontend

WORKDIR /opt/frontend

# Set argument for building.
ARG VITE_INSTANCE_NAME=Docker

# Copy the package files into the container.
COPY ./semantic-modeling-assistant-frontend/package.json ./semantic-modeling-assistant-frontend/package-lock.json ./

# Install from the lock file.
RUN npm ci

# Copy sources and build, use together with .dockerignore file.
COPY ./semantic-modeling-assistant-frontend/ .

# Build the repository.
RUN npm run build

# - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - #
FROM python:3.13.5

# Create a new user
RUN groupadd -g 1000 user && \
  useradd -m -u 1000 -g 1000 -s /bin/bash user

USER user

# Set the working directory in the container.
WORKDIR /opt/backend

# Copy the requirements file into the container.
COPY ./semantic-modeling-assistant-backend/requirements.txt .

# Install the Python dependencies.
RUN pip install --no-cache-dir -r requirements.txt

# Add pip install directory to path.
ENV PATH="/home/user/.local/bin:${PATH}"

# Copy the rest of the application code into the container
COPY ./semantic-modeling-assistant-backend/src/ .

# Expose the port the app runs on.
EXPOSE 5000

# Default env properties.
WORKDIR /data
ENV DATA_DIRECTORY=/data

# Copy frontend files.
WORKDIR /opt/frontend
COPY --from=frontend /opt/frontend/dist/ ./
ENV STATIC_DIRECTORY=/opt/frontend

# Get to the right directory.
WORKDIR /opt/backend/
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "80"]
