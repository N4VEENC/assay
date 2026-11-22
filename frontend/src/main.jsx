import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import '../../design/design-system.css';
import App from './App.jsx';
import { AuthProvider } from './auth.jsx';
import { Toast } from './components.jsx';

createRoot(document.getElementById('root')).render(
  <BrowserRouter><AuthProvider><App /><Toast /></AuthProvider></BrowserRouter>
);
