# Use the official Python image as a base image
FROM python:3.13.5

# Set the working directory in the container
WORKDIR /opt/semantic-modeling-assistant

# Copy the requirements file into the container
COPY requirements.txt .

# Install the Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application code into the container
COPY . .

# Expose the port the app runs on
EXPOSE 5000

# Default data storage path.
ENV DATA_DIRECTORY=/data

# Get to the right directory.
WORKDIR /opt/semantic-modeling-assistant/src
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000"]
