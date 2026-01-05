# Spring AI Recursive Language Models (RLMs)

A spring AI microservice implementing Recursive Language Models using Spring AI framework (not yet ready).
https://github.com/alexzhang13/rlm 

## Features

- 🔄 Recursive problem decomposition and solving
- 🎯 Multiple recursion strategies (Depth-First, Breadth-First)
- 💾 Caching support for improved performance
- 📊 Detailed thought process tracking
- 🔧 Configurable recursion depth and branching
- 🚀 RESTful API
- 📝 Comprehensive logging
- 🐳 Docker support

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- OpenAI API Key
- Docker (optional)

## Installation

1. Clone the repository
2. Set your OpenAI API key:
   ```bash
   export OPENAI_API_KEY=your-api-key-here

## example usage

```bash  
curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Calculate the sum of all prime numbers between 1 and 100",
    "maxDepth": 3,
    "maxBranching": 3,
    "strategy": "depth-first",
    "verbose": true
  }'

  curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Design a scalable microservices architecture for an e-commerce platform that handles 1 million users",
    "maxDepth": 4,
    "maxBranching": 4,
    "strategy": "depth-first",
    "verbose": true,
    "context": "The platform needs to support product catalog, user management, orders, and payments"
  }'

  curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Plan a 7-day trip to Japan covering Tokyo, Kyoto, and Osaka with a budget of $3000",
    "maxDepth": 3,
    "maxBranching": 3,
    "strategy": "breadth-first",
    "verbose": false
  }'


