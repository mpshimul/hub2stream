const axios = require('axios');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'https://h5-api.aoneroom.com/wefeed-h5api-bff/live/match-list-v5';
const LEAGUE_ID = '4186762757372631736';
const PAGE_SIZE = 200;

// Ensure the output directory exists
const DATA_DIR = path.join(__dirname, 'FIFAWorldCup2026API');
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

async function fetchMatches(getDirection, outputFile) {
  const url = `${BASE_URL}?leagueId=${LEAGUE_ID}&pageSize=${PAGE_SIZE}&getDirection=${getDirection}`;
  console.log(`Fetching: ${url}`);

  try {
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; GitHub-Action/1.0)',
        'Accept': 'application/json'
      },
      timeout: 30000
    });

    const data = response.data;

    // Check API response code
    if (data.code !== 0) {
      console.warn(`⚠️ API returned error code ${data.code}: ${data.message || 'unknown error'}`);
      console.warn(`   Skipping write to ${outputFile} – keeping previous data.`);
      return; // Do not overwrite
    }

    // Ensure we have a list (even if empty)
    const matchList = data.data?.list;
    if (!Array.isArray(matchList)) {
      console.warn(`⚠️ Unexpected response structure – no 'list' array found.`);
      console.warn(`   Skipping write to ${outputFile}.`);
      return;
    }

    // Optional: only write if there is at least one match
    // If you want to keep old data when the list is empty, uncomment this block:
    /*
    if (matchList.length === 0) {
      console.warn(`⚠️ Received empty match list for ${outputFile} – keeping previous file.`);
      return;
    }
    */

    // Write the file
    fs.writeFileSync(
      path.join(DATA_DIR, outputFile),
      JSON.stringify(data, null, 2),
      'utf8'
    );

    console.log(`✅ Saved to ${outputFile}`);
    console.log(`   Total matches: ${matchList.length}`);

  } catch (error) {
    console.error(`❌ Failed to fetch ${outputFile}:`, error.message);
    throw error; // Exit the workflow on network errors
  }
}

async function main() {
  console.log('🚀 Starting fetch at', new Date().toISOString());

  // getDirection=2 → upcoming matches
  // getDirection=1 → ended matches
  await fetchMatches(2, 'upcoming.json');
  await fetchMatches(1, 'ended.json');

  console.log('✅ All done!');
}

main().catch(error => {
  console.error('❌ Script failed:', error);
  process.exit(1);
});